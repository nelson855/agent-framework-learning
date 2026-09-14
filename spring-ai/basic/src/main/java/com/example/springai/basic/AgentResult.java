package com.example.springai.basic;

import java.util.List;

/** 一次对话的完整可观察结果：用户说了什么、模型调了哪些工具、最终答了什么。 */
public record AgentResult(String userInput, List<ToolCallTrace> toolCalls, String finalAnswer) {
}
