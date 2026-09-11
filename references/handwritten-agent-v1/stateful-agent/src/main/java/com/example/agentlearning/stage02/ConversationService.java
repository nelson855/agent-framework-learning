package com.example.agentlearning.stage02;

import java.util.List;

/**
 * 会话与对话历史的应用服务。
 *
 * <p>它只负责"会话外壳 + 消息流水账"这一类数据，不碰 Agent 状态 / 记忆 / 计划。
 * 这样可以让 Web / CLI 复用同一套会话逻辑，也明确"对话历史是一种数据"。
 */
public final class ConversationService {

    private final ConversationRepository conversations;
    private final MessageRepository messages;

    public ConversationService(ConversationRepository conversations, MessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    public Conversation createConversation(String title) {
        return conversations.create(title);
    }

    public List<Conversation> listConversations() {
        return conversations.findAll();
    }

    public Conversation findConversation(String id) {
        return conversations.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到会话: " + id));
    }

    public java.util.Optional<Conversation> findConversationById(String id) {
        return conversations.findById(id);
    }

    public List<StoredMessage> listMessages(String conversationId) {
        return messages.findByConversation(conversationId);
    }
}