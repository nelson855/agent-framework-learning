package com.example.agentlearning.stage04;

/**
 * 一条 Trace 事件：某人/某组件在某一时刻发生的一件事。
 *
 * @param workerId 可选：若由 Worker 产生则标记是哪个 Worker（Multi-Agent），null 表示 Orchestrator 主流程
 * @param dataJson 可选的附加结构化数据（JSON 文本），便于面板展示细节
 */
public record TraceEvent(
        String runId,
        String workerId,
        TraceEventType type,
        String message,
        String dataJson,
        long at) {

    public TraceEvent {
        workerId = workerId == null ? null : workerId;
        dataJson = dataJson == null ? "" : dataJson;
    }

    public static TraceEvent of(String runId, String workerId, TraceEventType type, String message) {
        return new TraceEvent(runId, workerId, type, message, "", System.currentTimeMillis());
    }

    public static TraceEvent of(String runId, String workerId, TraceEventType type, String message, long at) {
        return new TraceEvent(runId, workerId, type, message, "", at);
    }
}