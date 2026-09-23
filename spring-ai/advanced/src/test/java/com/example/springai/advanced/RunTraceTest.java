package com.example.springai.advanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * RunTrace 测试：验证四种事件能按顺序收集、属性完整、打印可读。
 */
class RunTraceTest {

    @Test
    void recordsFourKindsInOrder() {
        RunTrace trace = new RunTrace("run1");
        trace.record(RunTrace.Kind.MODEL_CALL, "prompt=…");
        trace.record(RunTrace.Kind.RETRIEVAL, "hits=2", Map.of("hitCount", 2));
        trace.record(RunTrace.Kind.MCP_TOOL_CALL, "get_task_status ok", Map.of("ok", true));
        trace.record(RunTrace.Kind.FINAL_RESPONSE, "len=42");

        List<RunTrace.Kind> kinds = trace.events().stream().map(RunTrace.Event::kind).toList();
        assertEquals(List.of(RunTrace.Kind.MODEL_CALL, RunTrace.Kind.RETRIEVAL,
                RunTrace.Kind.MCP_TOOL_CALL, RunTrace.Kind.FINAL_RESPONSE), kinds);
    }

    @Test
    void eventAttributesArePreserved() {
        RunTrace trace = new RunTrace("run1");
        trace.record(RunTrace.Kind.MCP_TOOL_CALL, "failed", Map.of("tool", "get_task_status", "ok", false));

        RunTrace.Event event = trace.events().get(0);
        assertEquals("get_task_status", event.attrs().get("tool"));
        assertEquals(false, event.attrs().get("ok"));
    }

    @Test
    void formatShowsRunIdAndKinds() {
        RunTrace trace = new RunTrace("abc123");
        trace.record(RunTrace.Kind.MODEL_CALL, "prompt=…");
        trace.record(RunTrace.Kind.FINAL_RESPONSE, "len=1");

        String printed = trace.format();
        assertTrue(printed.contains("run abc123"));
        assertTrue(printed.contains("MODEL_CALL"));
        assertTrue(printed.contains("FINAL_RESPONSE"));
    }
}
