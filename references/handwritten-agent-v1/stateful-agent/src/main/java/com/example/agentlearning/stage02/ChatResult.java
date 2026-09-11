package com.example.agentlearning.stage02;

import java.util.List;

/**
 * 一次 {@code chat} 的结果：给 Web / CLI 展示所需的一切后台真实数据。
 *
 * <p>Web 页面左边渲染 {@link #messages()}，右边渲染 {@link #run()}（State）、
 * {@link #plan()}（Plan）、{@link #retrievedMemories()}（Memory），
 * 全部来自后端 SQLite / 内存真实状态，不是在浏览器里伪造一套第二状态源。
 */
public record ChatResult(
        String conversationId,
        String runId,
        String answer,
        RunStatus status,
        int currentStep,
        Plan plan,
        List<Memory> retrievedMemories,
        List<StoredMessage> messages,
        boolean memorySaved,
        String memoryContent) {
}