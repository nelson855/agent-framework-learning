package com.example.springai.basic;

/** 一次工具调用的观察记录：调了哪个工具、传了什么参数、返回了什么。 */
public record ToolCallTrace(String toolName, String arguments, String result) {
}
