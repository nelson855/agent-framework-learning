package com.example.agentlearning.stage02;

import java.util.Map;
import java.util.function.Function;

/**
 * 一个已注册的可执行工具 = 定义（给模型看）+ 执行器（程序自己写）。
 *
 * <p>执行器是纯 Java 逻辑（查库、算数……），模型永远不直接碰它，
 * 只通过 {@link ToolCall} 表达"想调哪个、传什么参数"。
 */
public final class Tool {

    private final ToolDefinition definition;
    private final Function<Map<String, Object>, ToolResult> executor;

    public Tool(ToolDefinition definition, Function<Map<String, Object>, ToolResult> executor) {
        this.definition = definition;
        this.executor = executor;
    }

    public ToolDefinition definition() {
        return definition;
    }

    public ToolResult execute(Map<String, Object> arguments) {
        return executor.apply(arguments);
    }
}