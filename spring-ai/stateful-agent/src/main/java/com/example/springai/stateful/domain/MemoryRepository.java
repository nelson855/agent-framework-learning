package com.example.springai.stateful.domain;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆持久化，SQLite 直连。纯业务代码，不感知 AI。
 *
 * <p>检索只做关键字 LIKE，不做向量，对齐手写版。
 */
public class MemoryRepository implements AutoCloseable {

    private final Connection connection;

    public MemoryRepository(String jdbcUrl) {
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS memory (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            user_id TEXT NOT NULL,
                            memory_key TEXT NOT NULL,
                            memory_value TEXT NOT NULL,
                            created_at INTEGER NOT NULL
                        )
                        """);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("open memory database failed: " + jdbcUrl, e);
        }
    }

    /** 内存库，测试用。 */
    public static MemoryRepository inMemory() {
        return new MemoryRepository("jdbc:sqlite::memory:");
    }

    public synchronized Memory save(String userId, String memoryKey, String memoryValue) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO memory (user_id, memory_key, memory_value, created_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, userId);
            statement.setString(2, memoryKey);
            statement.setString(3, memoryValue);
            statement.setLong(4, Instant.now().toEpochMilli());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return new Memory(keys.getLong(1), userId, memoryKey, memoryValue);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("save memory failed", e);
        }
    }

    /** 按关键字在 key 和 value 里做 LIKE 检索，只查指定用户的，最多返回 limit 条。 */
    public synchronized List<Memory> findByKeyword(String userId, String keyword, int limit) {
        List<Memory> hits = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, user_id, memory_key, memory_value FROM memory "
                        + "WHERE user_id = ? AND (memory_key LIKE ? OR memory_value LIKE ?) "
                        + "ORDER BY id LIMIT ?")) {
            statement.setString(1, userId);
            statement.setString(2, "%" + keyword + "%");
            statement.setString(3, "%" + keyword + "%");
            statement.setInt(4, limit);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    hits.add(new Memory(
                            rows.getLong("id"),
                            rows.getString("user_id"),
                            rows.getString("memory_key"),
                            rows.getString("memory_value")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("retrieve memory failed", e);
        }
        return hits;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("close memory database failed", e);
        }
    }
}
