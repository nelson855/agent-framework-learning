package com.example.agentlearning.stage02;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 对话历史（Conversation History）的存取，对应 {@code message} 表。
 *
 * <p>这是"这个会话聊过什么"的完整流水账，和 memory 表（跨会话的长期记忆）、
 * agent_run 表（一次运行的执行状态）是三个不同的持久化维度。
 */
public final class MessageRepository {

    private final Database db;

    public MessageRepository(Database db) {
        this.db = db;
    }

    public StoredMessage append(String conversationId, String role, String content) {
        String id = "m-" + UUID.randomUUID().toString().substring(0, 8);
        StoredMessage message =
                new StoredMessage(id, conversationId, role, content, Instant.now().toString());
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO message (id, conversation_id, role, content, created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, message.id());
            ps.setString(2, message.conversationId());
            ps.setString(3, message.role());
            ps.setString(4, message.content());
            ps.setString(5, message.createdAt());
            ps.executeUpdate();
            return message;
        } catch (SQLException e) {
            throw new IllegalStateException("追加消息失败", e);
        }
    }

    public List<StoredMessage> findByConversation(String conversationId) {
        List<StoredMessage> result = new ArrayList<>();
        String sql = "SELECT id, conversation_id, role, content, created_at FROM message "
                + "WHERE conversation_id = ? ORDER BY created_at";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new StoredMessage(
                            rs.getString("id"),
                            rs.getString("conversation_id"),
                            rs.getString("role"),
                            rs.getString("content"),
                            rs.getString("created_at")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("查询对话历史失败: " + conversationId, e);
        }
    }

    public List<StoredMessage> findUserAndAssistantByConversation(String conversationId) {
        List<StoredMessage> result = new ArrayList<>();
        String sql = "SELECT id, conversation_id, role, content, created_at FROM message "
                + "WHERE conversation_id = ? AND role IN ('user','assistant') ORDER BY created_at";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new StoredMessage(
                            rs.getString("id"),
                            rs.getString("conversation_id"),
                            rs.getString("role"),
                            rs.getString("content"),
                            rs.getString("created_at")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("查询对话历史失败: " + conversationId, e);
        }
    }
}