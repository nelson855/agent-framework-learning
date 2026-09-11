package com.example.agentlearning.stage02;

/**
 * 一条已持久化的对话消息，对应 {@code message} 表的一行。
 *
 * <p>{@code role} 使用 wire 名（system / user / assistant），便于直接重建 LLM 消息。
 */
public record StoredMessage(String id, String conversationId, String role, String content, String createdAt) {
}