package com.example.agentlearning.stage04;

import java.util.Map;

/**
 * Orchestrator：把子任务交接给 Worker（Multi-Agent Handoff）。
 *
 * <p>维护一组 Worker，响应 {@code delegateTo(worker, task)}：记录 HANDOFF 事件，
 * 运行对应 Worker 子循环，把交接摘要作为结果返回给编排明链条。
 */
public final class Orchestrator {

    private final Map<String, WorkerAgent> workers;
    private final TraceService trace;

    public Orchestrator(Map<String, WorkerAgent> workers, TraceService trace) {
        this.workers = workers;
        this.trace = trace;
    }

    /** 把任务交接给指定 Worker，返回交接摘要。 */
    public ToolResult delegate(String runId, String workerId, String task) {
        WorkerAgent worker = workers.get(workerId);
        if (worker == null) {
            return ToolResult.fail("未知 Worker: " + workerId);
        }
        trace.record(TraceEvent.of(runId, null, TraceEventType.HANDOFF,
                "→ " + workerId + "：交接任务「" + task + "」"));
        String summary = worker.run(runId, workerId, task);
        trace.record(TraceEvent.of(runId, null, TraceEventType.HANDOFF,
                "← " + workerId + "：产出「" + abbreviate(summary) + "」"));
        return ToolResult.ok("Worker[" + workerId + "] 完成: " + summary);
    }

    public Map<String, WorkerAgent> workers() {
        return workers;
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() <= 120 ? s : s.substring(0, 117) + "...");
    }
}