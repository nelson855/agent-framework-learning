package com.example.springai.advanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP 边界测试：用 {@link InProcessTransport} 不启子进程，
 * 验证 tools/list 的 schema、tools/call 的命中/未命中/缺参数三种行为。
 */
class McpBoundaryTest {

    private TaskMcpClient client;

    @BeforeEach
    void setUp() {
        client = new TaskMcpClient(new InProcessTransport(TaskMcpServer.demo()));
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    @Test
    void discoverReturnsGetTaskStatusSchema() {
        List<TaskMcpClient.DiscoveredTool> tools = client.discover();

        assertEquals(1, tools.size());
        assertEquals("get_task_status", tools.get(0).name());
        assertTrue(tools.get(0).inputSchema().contains("task_id"),
                "schema 必须声明 task_id 参数，模型才知道怎么调");
    }

    @Test
    void callKnownTaskReturnsStatus() {
        String result = client.call("get_task_status", Map.of("task_id", "T-2"));

        assertTrue(result.contains("T-2"), "结果应带任务编号");
        assertTrue(result.contains("BLOCKED"), "T-2 的状态是 BLOCKED");
    }

    @Test
    void callUnknownTaskReturnsNotFoundText() {
        String result = client.call("get_task_status", Map.of("task_id", "T-99"));

        assertEquals("task not found: T-99", result);
    }

    @Test
    void callMissingArgumentThrowsMcpException() {
        TaskMcpClient.McpException error = assertThrows(TaskMcpClient.McpException.class,
                () -> client.call("get_task_status", Map.of()));

        assertTrue(error.getMessage().contains("task_id"), "错误信息应指出缺了哪个参数");
    }
}
