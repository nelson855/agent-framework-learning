package com.example.springai.advanced;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一次 Agent run 的结构化轨迹收集器（本章 Observability 的载体）。
 *
 * <p>@FW-CMP [TRACE] 本类对应手写版 {@code agent-harness/TraceService + TraceEvent}（打点记录器）。
 * 手写版在 AgentRunner 的每个关键步骤后手动调 {@code trace.record(...)}，
 * 产出的是散落的日志字符串；本类同样由业务代码手动埋点，
 * 但事件带类型（{@link Kind}）、时间戳与结构化属性，能回答
 * "检索发生在第几次模型调用之前"这类层次问题，而不只是"打过这行日志"。
 *
 * <p>生产环境的官方路径是 Micrometer Observation / OpenTelemetry，
 * 本章用轻量自研实现是为了离线可测，详见 README 的取舍说明。
 *
 * <p>完整对比见 {@code docs/comparisons/ch04_spring_ai_advanced.md}。
 */
public class RunTrace {

    /** 一次 run 里能区分的阶段，与 Prompt 04 的 Part C 要求一一对应。 */
    public enum Kind {
        MODEL_CALL,
        RETRIEVAL,
        MCP_TOOL_CALL,
        FINAL_RESPONSE
    }

    /** 单个轨迹事件：阶段 + 时间 + 一句话摘要 + 结构化属性。 */
    public record Event(Kind kind, Instant at, String summary, Map<String, Object> attrs) { }

    private final String runId;
    private final List<Event> events = new ArrayList<>();

    public RunTrace() {
        this(UUID.randomUUID().toString().substring(0, 6));
    }

    public RunTrace(String runId) {
        this.runId = runId;
    }

    public String runId() {
        return runId;
    }

    public synchronized void record(Kind kind, String summary) {
        record(kind, summary, Map.of());
    }

    public synchronized void record(Kind kind, String summary, Map<String, Object> attrs) {
        events.add(new Event(kind, Instant.now(), summary,
                Map.copyOf(new LinkedHashMap<>(attrs))));
    }

    /** 按记录顺序返回事件快照，供测试断言时序。 */
    public synchronized List<Event> events() {
        return List.copyOf(events);
    }

    public synchronized int size() {
        return events.size();
    }

    /** 树状打印一次 run 的层次，供演示输出。 */
    public synchronized String format() {
        StringBuilder out = new StringBuilder("run ").append(runId).append('\n');
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            boolean last = i == events.size() - 1;
            out.append(last ? "└─ " : "├─ ")
                    .append(event.at())
                    .append(' ')
                    .append(String.format("%-14s", event.kind()))
                    .append(event.summary());
            if (!event.attrs().isEmpty()) {
                out.append(" ").append(event.attrs());
            }
            out.append('\n');
        }
        return out.toString();
    }
}
