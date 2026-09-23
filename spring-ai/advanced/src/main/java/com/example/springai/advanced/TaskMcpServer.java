package com.example.springai.advanced;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 本地教学 MCP Server：只暴露一个只读工具 {@code get_task_status(task_id)}。
 *
 * <p>@FW-CMP 本类对应手写版 {@code agent-harness/ToolRegistry + ToolExecutor}（工具查表与执行）。
 * 手写版的工具是同进程 Java 方法，查表靠 Map、分派靠反射/直接调用；
 * 本类把同一个"查任务状态"能力搬到独立进程，调用方只能看到
 * {@code tools/list} 返回的 JSON Schema，看不到实现类。
 *
 * <p>传输无关：{@link #handleLine(String)} 收发都是 JSON 文本，
 * stdin/stdout 和同进程直调都走这一个入口。
 *
 * <p>完整对比见 {@code docs/comparisons/ch04_spring_ai_advanced.md}。
 */
public class TaskMcpServer {

    static final String TOOL_NAME = "get_task_status";

    private final TaskRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public TaskMcpServer(TaskRepository repository) {
        this.repository = repository;
    }

    /** 演示用 Server：T-1 OPEN、T-2 BLOCKED、T-3 DONE。 */
    public static TaskMcpServer demo() {
        return new TaskMcpServer(TaskRepository.demoData());
    }

    /**
     * 处理一行 JSON-RPC 请求，返回一行 JSON-RPC 响应（文本形式）。
     * 协议错误也包成正常响应返回，不抛异常，保证管道不中断。
     */
    public String handleLine(String requestLine) {
        try {
            JsonNode request = mapper.readTree(requestLine);
            JsonNode id = request.get("id");
            String method = request.hasNonNull("method") ? request.get("method").asText() : "";
            JsonNode result = switch (method) {
                case "tools/list" -> toolsList();
                case "tools/call" -> toolsCall(request.get("params"));
                default -> throw methodError("unknown method: " + method);
            };
            return response(id, result, null);
        } catch (MethodException e) {
            return errorResponse(extractId(requestLine), e.code, e.getMessage());
        } catch (Exception e) {
            return errorResponse(extractId(requestLine), -32603, "internal error: " + e.getMessage());
        }
    }

    private ObjectNode toolsList() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode taskId = props.putObject("task_id");
        taskId.put("type", "string");
        taskId.put("description", "任务编号，例如 T-1");
        schema.putArray("required").add("task_id");

        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", TOOL_NAME);
        tool.put("description", "查询任务当前状态，只读，无副作用");
        tool.set("inputSchema", schema);

        ObjectNode result = mapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        tools.add(tool);
        return result;
    }

    private ObjectNode toolsCall(JsonNode params) {
        if (params == null || !params.hasNonNull("name")) {
            throw methodError("tools/call requires params.name");
        }
        String name = params.get("name").asText();
        if (!TOOL_NAME.equals(name)) {
            throw methodError("unknown tool: " + name);
        }
        JsonNode args = params.get("arguments");
        if (args == null || !args.hasNonNull("task_id")
                || args.get("task_id").asText("").isBlank()) {
            throw methodError("get_task_status requires arguments.task_id");
        }
        String taskId = args.get("task_id").asText().strip();
        String text = repository.findById(taskId)
                .map(record -> "任务 " + record.id() + "：" + record.title()
                        + "，状态=" + record.status())
                .orElse("task not found: " + taskId);

        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode item = content.addObject();
        item.put("type", "text");
        item.put("text", text);
        return result;
    }

    private String response(JsonNode id, JsonNode result, JsonNode error) {
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("jsonrpc", "2.0");
        envelope.set("id", id == null ? mapper.nullNode() : id);
        if (result != null) {
            envelope.set("result", result);
        } else {
            envelope.set("error", error);
        }
        return envelope.toString();
    }

    private String errorResponse(JsonNode id, int code, String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);
        return response(id, null, error);
    }

    private JsonNode extractId(String requestLine) {
        try {
            JsonNode request = mapper.readTree(requestLine);
            return request.get("id");
        } catch (Exception e) {
            return null;
        }
    }

    private static MethodException methodError(String message) {
        return new MethodException(-32602, message);
    }

    /** 方法层错误（未知方法/未知工具/缺参数），对应 JSON-RPC -32602。 */
    private static class MethodException extends RuntimeException {
        final int code;

        MethodException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
