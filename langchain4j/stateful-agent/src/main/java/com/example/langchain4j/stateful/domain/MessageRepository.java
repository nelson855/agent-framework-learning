package com.example.langchain4j.stateful.domain;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 会话与消息持久化，SQLite 直连。纯业务代码，不感知 AI。
 *
 * <p>这是界面历史的家：完整流水账，供界面展示和审计。
 * 模型上下文另由框架 ChatMemory 维护，不在这里。
 */
public class MessageRepository implements AutoCloseable {

    private final Connection connection;

    public MessageRepository(String jdbcUrl) {
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS conversation (
                            id TEXT PRIMARY KEY,
                            user_id TEXT NOT NULL,
                            created_at INTEGER NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS message (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            conversation_id TEXT NOT NULL,
                            role TEXT NOT NULL,
                            content TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY (conversation_id) REFERENCES conversation(id)
                        )
                        """);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("open message database failed: " + jdbcUrl, e);
        }
    }

    /** 内存库，测试用。 */
    public static MessageRepository inMemory() {
        return new MessageRepository("jdbc:sqlite::memory:");
    }

    public synchronized String createConversation(String userId) {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO conversation (id, user_id, created_at) VALUES (?, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, userId);
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("create conversation failed", e);
        }
        return id;
    }

    public synchronized Message appendMessage(String conversationId, String role, String content) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO message (conversation_id, role, content, created_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, conversationId);
            statement.setString(2, role);
            statement.setString(3, content);
            statement.setLong(4, Instant.now().toEpochMilli());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return new Message(keys.getLong(1), conversationId, role, content);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("append message failed", e);
        }
    }

    public synchronized List<Message> listMessages(String conversationId) {
        List<Message> messages = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, conversation_id, role, content FROM message "
                        + "WHERE conversation_id = ? ORDER BY id")) {
            statement.setString(1, conversationId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    messages.add(new Message(
                            rows.getLong("id"),
                            rows.getString("conversation_id"),
                            rows.getString("role"),
                            rows.getString("content")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("list messages failed", e);
        }
        return messages;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("close message database failed", e);
        }
    }
}
