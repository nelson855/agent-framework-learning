package com.example.agentlearning.stage04;

import java.util.Map;

/**
 * 一个工具调用：模型"建议"执行的动作（工具名 + 参数）。
 *
 * <p>这只是模型的<b>建议</b>。能否执行、参数是否合法、执行后返回什么，
 * 全部由程序决定 —— Guardrail 正是插在这一层强制政策的。
 */
public record ToolCall(String name, Map<String, Object> arguments) {

    public ToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    @Override
    public String toString() {
        return name + arguments;
    }
}