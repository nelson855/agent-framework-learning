package com.example.agentlearning.stage04;

/**
 * ToolExecutor：真正执行工具的唯一入口，同时施加<b>高风险门禁</b>。
 *
 * <p>执行分派调用 {@link ToolRegistry#execute(ToolCall)}，并记录 TOOL_CALL / TOOL_RESULT 事件
 * 与 tool_event 明细（供 Tool Events 面板）。
 *
 * <p><b>门禁</b>：deleteTask 是高风险操作。只有调用方（AgentRunner）在人工批准后把
 * {@code highRiskApproved=true} 传进来，deleteTask 才会真正执行；否则一律返回失败。
 * 模型无论如何只能提出 tool_call 建议，无法绕过这里的安全判定。
 */
public final class ToolExecutor {

    /** deleteTask 未获批准时的失败标记。 */
    public static final String HIGH_RISK_DENIED = "高风险操作需人工批准";

    private final ToolRegistry registry;
    private final TraceService trace;

    public ToolExecutor(ToolRegistry registry, TraceService trace) {
        this.registry = registry;
        this.trace = trace;
    }

    /**
     * 执行一次工具调用。
     *
     * @param highRiskApproved 仅 deleteTask 需要：人工批准后才可为 true
     */
    public ToolResult execute(ToolCall call, String runId, String workerId, boolean highRiskApproved) {
        long t0 = System.currentTimeMillis();
        String args = abbreviate(call.arguments().toString());

        trace.record(TraceEvent.of(runId, workerId, TraceEventType.TOOL_CALL,
                call.name() + " " + abbreviate(call.arguments().toString())));

        ToolResult result;
        if ("deleteTask".equals(call.name()) && !highRiskApproved) {
            result = ToolResult.fail(HIGH_RISK_DENIED + ": " + call.arguments());
        } else {
            result = registry.execute(call);
        }

        trace.record(TraceEvent.of(runId, workerId, TraceEventType.TOOL_RESULT,
                (result.success() ? "OK " : "ERR ") + call.name() + " → " + abbreviate(result.message())));
        trace.recordTool(new TraceService.ToolEvent(runId, workerId, call.name(), args,
                result.success(), abbreviate(result.message()), System.currentTimeMillis() - t0,
                System.currentTimeMillis()));
        return result;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 160 ? s : s.substring(0, 157) + "...";
    }
}