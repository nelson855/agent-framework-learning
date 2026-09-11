package com.example.agentlearning.stage02;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：登记工具、按名分派、执行前校验、生成给模型的工具说明。
 */
public final class ToolRegistry {

    public static final String UNKNOWN_TOOL = "UNKNOWN_TOOL";

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.definition().name(), tool);
        return this;
    }

    /** 分派入口：查表 → 校验 → 执行。未知工具与参数错误都返回失败结果，不抛异常。 */
    public ToolResult execute(ToolCall call) {
        Tool tool = tools.get(call.name());
        if (tool == null) {
            return ToolResult.fail(UNKNOWN_TOOL + ": " + call.name());
        }
        String error = ArgumentValidator.validate(tool.definition(), call.arguments());
        if (error != null) {
            return ToolResult.fail("参数校验失败: " + error);
        }
        return tool.execute(call.arguments());
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(Tool::definition).toList();
    }

    /** 把工具说明序列化成 JSON，拼进系统提示让模型知道有哪些工具可用。 */
    public String toolsInstruction() {
        try {
            return objectMapper.writeValueAsString(definitions());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化工具说明失败", e);
        }
    }
}