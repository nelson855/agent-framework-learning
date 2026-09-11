package com.example.agentlearning.stage02;

/**
 * 一个会话外壳，对应 {@code conversation} 表的一行。
 */
public record Conversation(String id, String title, String createdAt) {
}