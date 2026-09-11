package com.example.agentlearning.stage02;

import java.util.List;
import java.util.UUID;

/**
 * 有状态的 Agent 运行器 —— Stage 02 的核心。
 *
 * <p>它把两个机制串起来：
 * <ol>
 *   <li><b>Agent Loop</b>：基于上下文连续决策，调用工具、得到 Observation，直到给出最终回答；</li>
 *   <li><b>Plan / Replan</b>：先按结构化计划分步执行（每步内部是一个小的 ReAct 循环），
 *       某步工具失败就请求 Replanner 重规划（有上限），而不是无脑重试。</li>
 * </ol>
 *
 * <p>持久化三件套各司其职：
 * <ul>
 *   <li>对话历史（user/assistant）写 {@code message} 表；</li>
 *   <li>执行状态（goal/status/currentStep）写 {@code agent_run} 表（Agent State）；</li>
 *   <li>计划与每步状态写 {@code plan} + {@code plan_step} 表（Plan）。</li>
 * </ul>
 * 运行细节的 Observation 只存在本轮内存上下文里，不入库。
 */
public final class StatefulAgentRunner {

    /** 单步内 ReAct 循环的最大模型决策次数（防止单步死循环）。 */
    public static final int MAX_STEPS_PER_STEP = 6;
    /** 计划执行过程中最多允许的重规划次数。 */
    public static final int MAX_REPLANS = 2;

    private final LlmClient llm;
    private final ToolRegistry tools;
    private final MessageRepository messages;
    private final AgentRunRepository runs;
    private final PlanRepository plans;
    private final MemoryRetriever retriever;
    private final Replanner replanner;

    public StatefulAgentRunner(
            LlmClient llm,
            ToolRegistry tools,
            MessageRepository messages,
            AgentRunRepository runs,
            PlanRepository plans,
            MemoryRetriever retriever,
            Replanner replanner) {
        this.llm = llm;
        this.tools = tools;
        this.messages = messages;
        this.runs = runs;
        this.plans = plans;
        this.retriever = retriever;
        this.replanner = replanner;
    }

    /**
     * 执行一次运行：拿到一个目标、一份计划，逐步执行并持久化状态。
     * 返回最终回答 + 落库后的运行状态。
     */
    public RunResult run(String conversationId, String userInput, Plan initialPlan) {
        String runId = "r-" + UUID.randomUUID().toString().substring(0, 8);
        AgentRun run = runs.create(runId, conversationId, userInput);
        plans.save(runId, initialPlan);

        // 开跑前检索长期记忆
        List<Memory> retrieved = retriever.retrieve(userInput, 3);
        AgentContext context = buildContext(conversationId, retrieved);

        System.out.println("RUN " + runId + " (goal=" + userInput + ")");

        Plan plan = initialPlan;
        int replans = 0;
        int idx = 0;
        while (idx < plan.steps().size()) {
            PlanStep step = plan.steps().get(idx);
            if (step.status() == PlanStepStatus.DONE || step.status() == PlanStepStatus.SKIPPED) {
                idx++;
                continue;
            }

            step.setStatus(PlanStepStatus.RUNNING);
            run = runs.updateStatus(runId, RunStatus.RUNNING, idx + 1);
            context.addSystem("当前执行计划步骤: [" + step.id() + "] " + step.description());
            System.out.println("EXEC STEP: [" + step.id() + "] " + step.description());

            StepOutcome outcome = executeStep(context, run, step);
            if (outcome.done()) {
                step.setStatus(PlanStepStatus.DONE);
                plans.updateStepStatuses(plan, runId);
                context.addAssistant(outcome.answer());
                System.out.println("STEP DONE: [" + step.id() + "] " + step.description());
                idx++;
                continue;
            }

            // 某步失败 → 请求 Replanner 重规划
            step.setStatus(PlanStepStatus.FAILED);
            step.setFailureReason(outcome.failureReason());
            plans.updateStepStatuses(plan, runId);
            System.out.println("STEP FAILED: [" + step.id() + "] " + step.description()
                    + " reason=" + outcome.failureReason());

            if (replans >= MAX_REPLANS) {
                System.out.println("REPLAN LIMIT REACHED: maxReplans=" + MAX_REPLANS);
                for (PlanStep s : plan.steps()) {
                    if (s.status() == PlanStepStatus.PENDING) {
                        s.setStatus(PlanStepStatus.SKIPPED);
                    }
                }
                run = runs.updateStatus(runId, RunStatus.FAILED, idx + 1);
                String msg = "计划前置步骤失败且重规划次数已用尽，任务未完成: " + outcome.failureReason();
                messages.append(conversationId, "assistant", msg);
                return new RunResult(runId, msg, run);
            }

            replans++;
            plan = replanner.replan(plan, step);
            plans.save(runId, plan);
            System.out.println("REPLAN -> " + plan.steps().size() + " steps");
            idx = 0; // 从新计划的头部重新评估（已完成步骤会跳过）
        }

        run = runs.updateStatus(runId, RunStatus.COMPLETED, plan.steps().size());
        String answer = buildFinalAnswer(context);
        messages.append(conversationId, "assistant", answer);
        System.out.println("FINAL: " + answer);
        return new RunResult(runId, answer, run);
    }

    /** 执行单个计划步骤：内部是一个小的 ReAct 循环，直到模型给出 final。 */
    private StepOutcome executeStep(AgentContext context, AgentRun run, PlanStep step) {
        for (int i = 0; i < MAX_STEPS_PER_STEP; i++) {
            LlmResponse reply = llm.chat(context.messages());
            AgentDecision decision = AgentDecisionParser.parse(reply.content());
            System.out.println("MODEL_ACTION: " + decision.type());

            if (decision.isFinal()) {
                return StepOutcome.done(decision.answer());
            }

            ToolCall call = decision.toolCall();
            System.out.println("TOOL_CALL: " + call.name() + call.arguments());
            run = runs.updateStatus(run.runId(), RunStatus.WAITING_TOOL, planStepIndex(step));

            ToolResult result = tools.execute(call);
            context.addAssistant(reply.content());
            context.addObservation(call, result, result.success());
            System.out.println("TOOL_RESULT: " + result.message());

            if (!result.success()) {
                // 工具失败 → 这一步失败，需要 Replan
                return StepOutcome.failed(result.message());
            }
        }
        return StepOutcome.failed("单步 ReAct 循环超过最大步数 " + MAX_STEPS_PER_STEP);
    }

    /** 从步骤 id 提取序号（S1 -> 1），用于写 currentStep。 */
    private int planStepIndex(PlanStep step) {
        try {
            return Integer.parseInt(step.id().substring(1));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** 把所有已完成计划的步骤回答拼成一段最终总结。 */
    private String buildFinalAnswer(AgentContext context) {
        StringBuilder sb = new StringBuilder("任务已完成。结果为：\n");
        for (Message msg : context.messages()) {
            if (msg.role() == Role.ASSISTANT) {
                sb.append("- ").append(msg.content()).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    private AgentContext buildContext(String conversationId, List<Memory> retrieved) {
        AgentContext context = new AgentContext();
        context.addSystem(systemPrompt());

        if (!retrieved.isEmpty()) {
            System.out.println("RETRIEVED MEMORY");
            StringBuilder block = new StringBuilder("[RETRIEVED MEMORY]\n");
            for (Memory memory : retrieved) {
                System.out.println("  - [" + memory.type() + "] " + memory.content());
                block.append("- [").append(memory.type()).append("] ").append(memory.content()).append('\n');
            }
            context.addSystem(block.toString().stripTrailing());
        }

        // 从库里重建已有对话历史（含刚追加的用户消息），保证多轮/重启后连续
        for (StoredMessage stored : messages.findUserAndAssistantByConversation(conversationId)) {
            if ("user".equals(stored.role())) {
                context.addUser(stored.content());
            } else if ("assistant".equals(stored.role())) {
                context.addAssistant(stored.content());
            }
        }
        return context;
    }

    private String systemPrompt() {
        return """
                你是一个有状态的控制台 AI 任务助手。你可以调用工具完成用户的任务。
                当前可用的工具（JSON 数组描述）：
                %s

                决策协议（只输出一个 JSON，不要输出任何其他文字）：
                1. 需要调用工具时：
                   {"type":"tool_call","tool":"工具名","arguments":{参数名:值,...},"decisionSummary":"这一步为什么这么做，一句话"}
                2. 当前计划步骤的任务已完成、可以回答用户时：
                   {"type":"final","answer":"给用户的最终回答"}

                对话中带 [observation] 前缀的消息是工具执行后的观察结果，请基于它继续决策；
                不要重复执行一个已经执行过、且结果已知的工具调用。
                带 [RETRIEVED MEMORY] 前缀的是从长期记忆中检索到的用户信息，回答时应当尊重这些偏好。
                带"当前执行计划步骤"的是你正在执行的一步计划，请完成它并给出 final。
                如果某个工具调用失败，请调整你的方案重新尝试。
                """.formatted(tools.toolsInstruction());
    }

    /** 单个计划步骤的执行结果。 */
    private record StepOutcome(boolean done, String answer, String failureReason) {

        static StepOutcome done(String answer) {
            return new StepOutcome(true, answer, null);
        }

        static StepOutcome failed(String reason) {
            return new StepOutcome(false, null, reason);
        }
    }
}