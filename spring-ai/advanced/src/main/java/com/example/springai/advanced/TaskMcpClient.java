package com.example.springai.advanced;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP Client：负责工具发现（tools/list）与远程调用（tools/call）。
 *
 * <p>@FW-CMP 本类在手写版里没有对应物——手写版的工具全是同进程方法，
 * 调用方直接拿 `Tool` 接口调，没有"发现"这一步。
 * 本类的存在本身就是 MCP 改变 Tool 边界的证据：调用前要先问 Server"你有什么工具"。
 *
 * <p>完整对比见 {@code docs/comparisons/ch04_spring_ai_advanced.md}。
 *
 * <p>调用方只和本类打交道，不直接碰 JSON-RPC 文本：
 * {@link #discover()} 返回结构化的工具描述，{@link #call} 接受参数 Map、返回结果文本。
 * 传输层（跨进程还是同进程）由构造时传入的 {@link JsonRpcTransport} 决定。
 */
public class TaskMcpClient implements AutoCloseable {

    /** 传输/协议层失败时抛出，业务层错误（任务不存在）不经过它。 */
    public static class McpException extends RuntimeException {
        public McpException(String message) {
            super(message);
        }

        public McpException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** tools/list 发现到的单个工具：名字 + 描述 + JSON Schema（字符串形式）。 */
    public record DiscoveredTool(String name, String description, String inputSchema) { }

    private final JsonRpcTransport transport;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong nextId = new AtomicLong(1);

    public TaskMcpClient(JsonRpcTransport transport) {
        this.transport = transport;
    }

    /** 发 tools/list，返回 Server 暴露的工具清单。 */
    public List<DiscoveredTool> discover() {
        JsonNode result = request("tools/list", mapper.createObjectNode());
        List<DiscoveredTool> tools = new ArrayList<>();
        for (JsonNode tool : result.path("tools")) {
            tools.add(new DiscoveredTool(
                    tool.path("name").asText(),
                    tool.path("description").asText(""),
                    tool.path("inputSchema").toString()));
        }
        return tools;
    }

    /**
     * 调 tools/call，返回结果文本（如"任务 T-2：...，状态=BLOCKED"）。
     * 任务不存在也走正常返回（"task not found: ..."），只有协议/传输故障才抛 {@link McpException}。
     */
    public String call(String toolName, Map<String, Object> arguments) {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", mapper.valueToTree(arguments));
        JsonNode result = request("tools/call", params);
        StringBuilder text = new StringBuilder();
        for (JsonNode item : result.path("content")) {
            if ("text".equals(item.path("type").asText())) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(item.path("text").asText());
            }
        }
        return text.toString();
    }

    private JsonNode request(String method, ObjectNode params) {
        long id = nextId.getAndIncrement();
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id);
        envelope.put("method", method);
        envelope.set("params", params);
        String responseLine;
        try {
            responseLine = transport.exchange(envelope.toString());
        } catch (TaskMcpClient.McpException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new McpException(method + " transport failed", e);
        }
        JsonNode response;
        try {
            response = mapper.readTree(responseLine);
        } catch (Exception e) {
            throw new McpException(method + " bad response: " + responseLine, e);
        }
        if (response.hasNonNull("error")) {
            JsonNode error = response.get("error");
            throw new McpException(method + " error " + error.path("code").asInt()
                    + ": " + error.path("message").asText());
        }
        if (!response.has("result")) {
            throw new McpException(method + " missing result: " + responseLine);
        }
        return response.get("result");
    }

    @Override
    public void close() {
        transport.close();
    }
}
