package com.example.agentlearning.stage04;

import java.util.List;

/**
 * ModelClient：模型的薄封装。每次调用记录一条 MODEL_CALL Trace 事件。
 *
 * <p>它隔离开「底层模型供应商」与「Agent 逻辑」——删除它，Agent 失去「模型调用可观测」，
 * 但模型本身通过 {@link LlmClient} 仍可调用。
 */
public final class ModelClient {

    private final LlmClient llm;
    private final TraceService trace;

    public ModelClient(LlmClient llm, TraceService trace) {
        this.llm = llm;
        this.trace = trace;
    }

    /** 调用模型，返回决策文本；workerId 为 null 表示 Orchestrator 主流程。 */
    public String chat(String runId, String workerId, List<Message> context) {
        String reply = llm.chat(context).content();
        trace.record(TraceEvent.of(runId, workerId, TraceEventType.MODEL_CALL,
                "llm(" + (workerId == null ? "orchestrator" : workerId) + ") n=" + context.size()
                        + " → " + abbreviate(reply)));
        return reply;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 160 ? s : s.substring(0, 157) + "...";
    }
}