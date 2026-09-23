package com.example.springai.advanced;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 把 MCP Server 上发现的工具，包装成 Spring AI 能直接挂载的 {@link ToolCallback}。
 *
 * <p>@FW-CMP 本类对应手写版 {@code agent-harness/Tool}（工具定义接口）。
 * 手写版的工具是"接口 + 实现类"写死在同一进程，名字、参数说明都在 Java 代码里；
 * 本类的名字、描述、参数 Schema 全来自 MCP {@code tools/list} 的返回，
 * 本地没有任何写死的工具定义——工具是在运行时"发现"出来的。
 *
 * <p>完整对比见 {@code docs/comparisons/ch04_spring_ai_advanced.md}。
 */
public class McpTaskToolCallback implements ToolCallback {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<>() { };

    private final TaskMcpClient client;
    private final TaskMcpClient.DiscoveredTool tool;
    private final ToolDefinition definition;
    private final RunTrace trace;
    private final ObjectMapper mapper = new ObjectMapper();

    public McpTaskToolCallback(TaskMcpClient client, TaskMcpClient.DiscoveredTool tool, RunTrace trace) {
        this.client = client;
        this.tool = tool;
        this.trace = trace;
        this.definition = ToolDefinition.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(tool.inputSchema())
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    // @FW-CMP [TOOL_DISPATCH] 工具分派：本地反射调用 → 远程 JSON-RPC 调用
    //   手写（agent-harness/ToolExecutor.execute，大意）：
    //     Tool tool = registry.get(call.name());   // 同进程 Map 查表
    //     return tool.execute(call.arguments());    // 直接调 Java 方法
    //   框架+远程（下方 call 方法）：
    //     client.call(tool.name(), arguments);     // JSON-RPC 发到独立进程
    //     // 查表发生在 Server 端，分派结果经管道返回
    //   差异：调用从"纳秒级方法调用"变成"毫秒级进程间请求"，失败面多了
    //   管道断开、Server 崩溃、版本不一致三类；业务错误（任务不存在）
    //   仍返回字符串让模型重试，只有传输故障才抛 McpException 中断。
    //   完整版见 docs/comparisons/ch04_spring_ai_advanced.md#4-tool-dispatch
    @Override
    public String call(String toolInput) {
        Map<String, Object> arguments = parseArguments(toolInput);
        try {
            String result = client.call(tool.name(), arguments);
            trace.record(RunTrace.Kind.MCP_TOOL_CALL,
                    tool.name() + arguments + " -> " + abbreviate(result),
                    Map.of("tool", tool.name(), "ok", true));
            return result;
        } catch (TaskMcpClient.McpException e) {
            trace.record(RunTrace.Kind.MCP_TOOL_CALL,
                    tool.name() + arguments + " FAILED: " + e.getMessage(),
                    Map.of("tool", tool.name(), "ok", false));
            throw e;
        }
    }

    private Map<String, Object> parseArguments(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(toolInput, MAP_TYPE);
        } catch (Exception e) {
            throw new TaskMcpClient.McpException("bad tool arguments: " + toolInput, e);
        }
    }

    private static String abbreviate(String text) {
        if (text != null && text.length() > 80) {
            return text.substring(0, 80) + "…";
        }
        return String.valueOf(text);
    }
}
