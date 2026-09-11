package com.example.agentlearning.stage04;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表：登记工具、按名分派、执行前校验、生成给模型的工具说明。
 *
 * <p>{@link #execute(ToolCall)} 是本模块<b>唯一真正执行工具的地方</b>。
 * Guardrail 校验 + HITL 批准的删除只会走到这里，保证模型无论如何都绕不过。
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 未注册工具时返回的错误标记。 */
    public static final String UNKNOWN_TOOL = "UNKNOWN_TOOL";

    public ToolRegistry register(Tool tool) {
        tools.put(tool.definition().name(), tool);
        return this;
    }

    /** 分派一个工具调用；找不到工具或参数不合法时返回失败结果，而不是抛异常。 */
    public ToolResult execute(ToolCall call) {
        Tool tool = tools.get(call.name());
        if (tool == null) {
            return ToolResult.fail(UNKNOWN_TOOL + ": " + call.name() + "（模型建议了一个未注册的工具）");
        }
        String error = ArgumentValidator.validate(tool.definition(), call.arguments());
        if (error != null) {
            return ToolResult.fail("参数校验失败: " + error);
        }
        return tool.execute(call.arguments());
    }

    /** 所有已注册工具的定义（保持注册顺序）。 */
    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(Tool::definition).toList();
    }

    /** 生成给模型看的工具说明（JSON 数组文本），写入 system prompt。 */
    public String toolsInstruction() {
        try {
            return objectMapper.writeValueAsString(definitions());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化工具说明失败", e);
        }
    }
}