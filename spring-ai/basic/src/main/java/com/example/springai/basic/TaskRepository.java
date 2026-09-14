package com.example.springai.basic;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务持久化，SQLite 直连。纯业务代码，不感知 AI。
 *
 * <p>对照手写版 TaskStore：建表读写原样保留，持久化边界没动，框架对这张表一无所知。
 *
 * <p>传文件路径就是文件库（演示用，可观察真实副作用）；
 * 传 {@code :memory:} 就是内存库（测试用）。
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

    /** 文件库，演示用。 */
    public static TaskRepository fileBased(String path) {
        return new TaskRepository("jdbc:sqlite:" + path);
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

    public synchronized List<Task> findAll() {
        List<Task> tasks = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, title, status FROM task ORDER BY id")) {
            while (rows.next()) {
                tasks.add(new Task(
                        rows.getString("id"),
                        rows.getString("title"),
                        TaskStatus.valueOf(rows.getString("status"))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("list tasks failed", e);
        }
        return tasks;
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
