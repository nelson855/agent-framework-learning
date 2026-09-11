package com.example.agentlearning.stage02;

/**
 * 一条长期记忆（Long-term Memory），对应 {@code memory} 表的一行。
 *
 * <p>与对话历史不同：memory 是<b>经过选择</b>、跨会话仍有价值的信息
 * （偏好、约定、事实），而不是每条消息都存。
 */
public record Memory(
        String id,
        String userId,
        String type,
        String content,
        int importance,
        String createdAt,
        String lastUsedAt) {
}