package com.example.agentlearning.stage04;

import java.util.Map;

/**
 * 一个工具的定义：告诉模型"这个工具有什么、怎么调用"。
 *
 * <p>{@code parameters} 是参数名到类型描述（{@code "string"} / {@code "number"}）的映射，
 * 声明出来的每个参数都视为<b>必填</b>。
 */
public record ToolDefinition(String name, String description, Map<String, String> parameters) {

    public ToolDefinition {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}