package com.example.agentlearning.stage02;

import java.util.Map;

/**
 * 一次工具调用请求：模型建议"调用哪个工具、传什么参数"。
 */
public record ToolCall(String name, Map<String, Object> arguments) {

    @Override
    public String toString() {
        return name + arguments;
    }
}