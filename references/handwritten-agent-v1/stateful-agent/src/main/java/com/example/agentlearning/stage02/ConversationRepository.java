package com.example.agentlearning.stage02;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 会话外壳的存取，对应 {@code conversation} 表。
 */
public final class ConversationRepository {

    private final Database db;

    public ConversationRepository(Database db) {
        this.db = db;
    }

    public Conversation create(String title) {
        String id = "c-" + UUID.randomUUID().toString().substring(0, 8);
        Conversation conversation = new Conversation(id, title, Instant.now().toString());
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO conversation (id, title, created_at) VALUES (?, ?, ?)")) {
            ps.setString(1, conversation.id());
            ps.setString(2, conversation.title());
            ps.setString(3, conversation.createdAt());
            ps.executeUpdate();
            return conversation;
        } catch (SQLException e) {
            throw new IllegalStateException("创建会话失败", e);
        }
    }

    public Optional<Conversation> findById(String id) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, title, created_at FROM conversation WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(fromRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("查询会话失败: " + id, e);
        }
    }

    public List<Conversation> findAll() {
        List<Conversation> result = new ArrayList<>();
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery("SELECT id, title, created_at FROM conversation ORDER BY created_at")) {
            while (rs.next()) {
                result.add(fromRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("列出会话失败", e);
        }
    }

    private Conversation fromRow(ResultSet rs) throws SQLException {
        return new Conversation(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("created_at"));
    }
}