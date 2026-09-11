package com.example.agentlearning.stage04;

/**
 * 工具执行结果：是否成功 + 一段可展示的文本。
 *
 * <p>失败也是一种结果（参数不合法、工具不存在、业务规则拒绝、人工拒绝），
 * 不应当让程序抛异常崩溃。在 Agent 语义里，它就是一次 <b>Observation</b>，
 * 会被追加进上下文，影响模型下一步决策。
 */
public record ToolResult(boolean success, String message) {

    public static ToolResult ok(String message) {
        return new ToolResult(true, message);
    }

    public static ToolResult fail(String message) {
        return new ToolResult(false, message);
    }
}