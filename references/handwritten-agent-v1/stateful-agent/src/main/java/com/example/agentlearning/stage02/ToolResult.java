package com.example.agentlearning.stage02;

/**
 * 工具执行结果：是否成功 + 一段可展示的文本。
 */
public record ToolResult(boolean success, String message) {

    public static ToolResult ok(String message) {
        return new ToolResult(true, message);
    }

    public static ToolResult fail(String message) {
        return new ToolResult(false, message);
    }
}