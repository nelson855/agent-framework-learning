package com.example.agentlearning.stage03;

import java.util.ArrayList;
import java.util.List;

/**
 * Long-running Agent 核心循环：每次执行一步，保存版本化 Checkpoint。
 *
 * <p>每一步：
 * <pre>
 * index = NEXT_PENDING
 *   → 若 CrashPolicy 命中：throw SimulatedCrashException（受控中断）
 *   → 检索 RAG + 记忆，构建上下文快照
 *   → 执行步骤工具，记录结果
 *   → maybeCompact（超阈值则压缩）
 *   → checkpointRepository.save(state)  打印 CHECKPOINT SAVED
 * </pre>
 *
 * <p>Resume 时从外部加载最新 Checkpoint 得到 state 再 step()，
 * DONE 步骤天然不会重复执行。
 */
public final class LongRunningRunner {

    private final StepExecutor executor;
    private final CheckpointRepository checkpoints;
    private final RunRepository runs;
    private final ContextBuilder contextBuilder;
    private final Compactor compactor;
    private final KnowledgeRetriever knowledgeRetriever;
    private final MemoryRetriever memoryRetriever;
    private final CrashPolicy crashPolicy;

    public LongRunningRunner(
            StepExecutor executor,
            CheckpointRepository checkpoints,
            RunRepository runs,
            ContextBuilder contextBuilder,
            Compactor compactor,
            KnowledgeRetriever knowledgeRetriever,
            MemoryRetriever memoryRetriever,
            CrashPolicy crashPolicy) {
        this.executor = executor;
        this.checkpoints = checkpoints;
        this.runs = runs;
        this.contextBuilder = contextBuilder;
        this.compactor = compactor;
        this.knowledgeRetriever = knowledgeRetriever;
        this.memoryRetriever = memoryRetriever;
        this.crashPolicy = crashPolicy;
    }

    /** 执行一个步骤；返回推进后的状态 {@link StepOutcome}。 */
    public StepOutcome step(AgentState state) {
        if (state.isComplete()) {
            runs.update(state.runId(), RunStatus.COMPLETED, state.totalSteps());
            return new StepOutcome(RunStatus.COMPLETED, "", -1, "所有步骤已完成");
        }

        int index = state.nextPendingStepIndex();
        PlanStep step = state.plan().get(index);

        if (crashPolicy.shouldCrash(index)) {
            throw new SimulatedCrashException(index,
                    "SIMULATED INTERRUPTION before step " + (index + 1) + ": " + step.id());
        }

        // 检索并构建上下文（含快照），为"本步能看到什么"做铺垫
        List<KnowledgeDoc> ragDocs = knowledgeRetriever.retrieve(state.goal(), 3);
        List<Memory> memories = memoryRetriever.retrieve(state.goal(), 3);
        contextBuilder.build(state.runId(), state, index, ragDocs, memories);

        // 执行工具
        String result = executor.execute(step.tool(), step.args());
        System.out.println("[STEP " + (index + 1) + "] " + step.id() + " → " + trim(result));

        // 更新 plan 与 results
        List<PlanStep> plan = new ArrayList<>(state.plan());
        plan.set(index, step.withResult(result));
        List<String> results = new ArrayList<>(state.stepResults());
        results.add(step.id() + ": " + result);

        AgentState next = new AgentState(state.runId(), state.goal(), List.copyOf(plan),
                List.copyOf(results), state.compacted());

        // 压缩检测
        next = compactor.maybeCompact(next);

        // 保存 Checkpoint（新版本，不覆盖）
        int version = checkpoints.save(next);
        System.out.println("CHECKPOINT SAVED version=" + version);

        // 推进 run 记录
        runs.update(state.runId(), null, index);

        if (next.isComplete()) {
            runs.update(state.runId(), RunStatus.COMPLETED, next.totalSteps());
            return new StepOutcome(RunStatus.COMPLETED, step.id(), index, "完成 " + step.id());
        }
        return new StepOutcome(RunStatus.RUNNING, step.id(), index, "执行了 " + step.id());
    }

    private static String trim(String text) {
        if (text == null || text.length() <= 60) {
            return text;
        }
        return text.substring(0, 60) + "…";
    }
}