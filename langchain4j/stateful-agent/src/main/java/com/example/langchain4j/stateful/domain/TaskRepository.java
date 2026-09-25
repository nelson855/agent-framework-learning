package com.example.langchain4j.stateful.domain;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务持久化，SQLite 直连。纯业务代码，不感知 AI。
 *
 * <p>传 {@code :memory:} 就是内存库（测试用）；传文件路径就是文件库。
 */
public class TaskRepository implements AutoCloseable {

    private final Connection connection;
    private final AtomicLong idSequence = new AtomicLong(0);

    public TaskRepository(String jdbcUrl) {
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS task (
                            id TEXT PRIMARY KEY,
                            title TEXT NOT NULL,
                            status TEXT NOT NULL
                        )
                        """);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("open task database failed: " + jdbcUrl, e);
        }
    }

    /** 内存库，测试用。 */
    public static TaskRepository inMemory() {
        return new TaskRepository("jdbc:sqlite::memory:");
    }

    public synchronized Task save(String title) {
        String id = "T-" + idSequence.incrementAndGet();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO task (id, title, status) VALUES (?, ?, ?)")) {
            statement.setString(1, id);
            statement.setString(2, title);
            statement.setString(3, TaskStatus.OPEN.name());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("save task failed", e);
        }
        return new Task(id, title, TaskStatus.OPEN);
    }

    public synchronized Optional<Task> findById(String id) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, title, status FROM task WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Task(
                        rows.getString("id"),
                        rows.getString("title"),
                        TaskStatus.valueOf(rows.getString("status"))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("find task failed: " + id, e);
        }
    }

    public synchronized void updateStatus(String id, TaskStatus status) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE task SET status = ? WHERE id = ?")) {
            statement.setString(1, status.name());
            statement.setString(2, id);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("task not found: " + id);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("update task failed: " + id, e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("close task database failed", e);
        }
    }
}
