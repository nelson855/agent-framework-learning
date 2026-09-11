package com.example.agentlearning.stage03;

/**
 * 一条长期记忆，对应 {@code memory} 表。
 * 语义：关于用户——偏好、约定、事实。与知识文档来源不同、用途不同。
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