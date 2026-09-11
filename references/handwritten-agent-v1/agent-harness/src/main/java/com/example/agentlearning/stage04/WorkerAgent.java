package com.example.agentlearning.stage04;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WorkerAgent：一个独立的子 Agent 循环（Multi-Agent）。
 *
 * <p>由 {@link Orchestrator} 通过 {@code delegateTo} 派发一个子任务，Worker 用自己的上下文
 * 独立执行几个步骤（可调工具，但不含高风险删除、不再 handoff，避免递归），返回交接摘要。
 * 它产生的 Trace 事件会用 {@code workerId} 标记，便于 Web 的 Worker/Handoff 面板追踪。
 */
public final class WorkerAgent {

    private final ContextBuilder contextBuilder;
    private final ModelClient model;
    private final ToolExecutor toolExec;
    private final TraceService trace;
    private final int maxSteps;

    public WorkerAgent(ContextBuilder contextBuilder, ModelClient model,
                       ToolExecutor toolExec, TraceService trace, int maxSteps) {
        this.contextBuilder = contextBuilder;
        this.model = model;
        this.toolExec = toolExec;
        this.trace = trace;
        this.maxSteps = maxSteps;
    }

    /** 运行一个子任务，返回交接摘要（final 回答）。 */
    public String run(String runId, String workerId, String task) {
        List<Message> context = contextBuilder.build(runId, workerId, task, List.of(), "任务: " + task);
        for (int step = 0; step < maxSteps; step++) {
            String reply = model.chat(runId, workerId, context);
            AgentDecision decision = AgentDecisionParser.parse(reply);
            if (decision.isFinal()) {
                return decision.answer();
            }
            // Worker 永不执行高风险删除：highRiskApproved 始终 false
            ToolResult result = toolExec.execute(decision.toolCall(), runId, workerId, false);
            context.add(Message.assistant(reply));
            context.add(Message.user(observation(decision.toolCall(), result)));
        }
        return "WORKER_MAX_STEPS";
    }

    private static String observation(ToolCall call, ToolResult result) {
        return "[observation] " + call + " => " + result.message();
    }
}