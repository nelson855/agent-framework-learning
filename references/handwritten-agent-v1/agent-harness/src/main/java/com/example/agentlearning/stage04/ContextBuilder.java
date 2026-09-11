package com.example.agentlearning.stage04;

import java.util.ArrayList;
import java.util.List;

/**
 * ContextBuilder：把「给模型看的内容」拼装成一段上下文（消息列表）。
 *
 * <p>组装：system（工具说明 + 决策协议 + 计划）+ 用户目标 + 检索到的工作记忆。
 * 每次 build 记录 CONTEXT_BUILT；每次注入记忆记录 MEMORY_RETRIEVED。
 * 删除它，Agent 失去「给模型的上下文拼装」——模型仍在，只是没人把工具与目标组织给它。
 */
public final class ContextBuilder {

    private final ToolProvider tools;
    private final MemoryStore memory;
    private final TraceService trace;

    public ContextBuilder(ToolProvider tools, MemoryStore memory, TraceService trace) {
        this.tools = tools;
        this.memory = memory;
        this.trace = trace;
    }

    /** 构建基础上下文（system + 用户目标 + 初始记忆命中）。workerId 为 null 表示主流程。 */
    public List<Message> build(String runId, String workerId, String goal,
                               List<PlanStep> plan, String userMessage) {
        List<Message> ctx = new ArrayList<>();
        ctx.add(Message.system(systemPrompt(plan)));
        ctx.add(Message.user(userMessage));

        List<String> hits = memory.retrieve(goal);
        if (!hits.isEmpty()) {
            for (String hit : hits) {
                ctx.add(Message.user("[memory retrieved] " + hit));
            }
            trace.record(TraceEvent.of(runId, workerId, TraceEventType.MEMORY_RETRIEVED,
                    "检索到记忆 " + hits.size() + " 条"));
        }
        trace.record(TraceEvent.of(runId, workerId, TraceEventType.CONTEXT_BUILT,
                "构建上下文 n=" + ctx.size() + "，计划 " + plan.size() + " 步"));
        return ctx;
    }

    /** 在循环中重新检索记忆并注入新增命中（remember 写入后模型即可看到）。返回注入后计数。 */
    public int injectMemory(List<Message> ctx, String runId, String workerId,
                            String goal, int alreadyInjected) {
        List<String> hits = memory.retrieve(goal);
        int injected = alreadyInjected;
        for (int i = injected; i < hits.size(); i++) {
            ctx.add(Message.user("[memory retrieved] " + hits.get(i)));
            injected++;
        }
        if (injected > alreadyInjected) {
            trace.record(TraceEvent.of(runId, workerId, TraceEventType.MEMORY_RETRIEVED,
                    "新增注入记忆 " + (injected - alreadyInjected) + " 条"));
        }
        return injected;
    }

    private String systemPrompt(List<PlanStep> plan) {
        StringBuilder planText = new StringBuilder();
        int n = 1;
        for (PlanStep s : plan) {
            planText.append(n++).append(". ").append(s.description()).append("\n");
        }
        return """
                你是一个 Harness 编排 Agent。可以调用下面的工具：
                %s

                本轮计划（供参考）：
                %s

                决策协议（只输出一个 JSON，不要输出任何其他文字）：
                1. 需要调用工具时：
                   {"type":"tool_call","tool":"工具名","arguments":{...},"decisionSummary":"一步解释"}
                2. 已有足够信息可回答时：
                   {"type":"final","answer":"给用户的最终回答"}

                安全规则：deleteTask 是高风险操作，会提交人工审批；审批前不会真正删除。
                对话中带 [observation] / [memory retrieved] 前缀的消息是工具执行或记忆检索结果，
                请基于它们继续决策；不要重复执行已失败或已在等待审批的调用。
                """.formatted(tools.toolsInstruction(), planText.toString().trim());
    }
}