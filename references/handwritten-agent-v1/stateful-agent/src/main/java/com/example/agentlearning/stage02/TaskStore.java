package com.example.agentlearning.stage02;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 任务的 SQLite 存储（JDBC 直连），对应 {@code task} 表。
 *
 * <p>这是工具操作的目标数据：模型通过 {@code createTask} / {@code listTasks} 等工具
 * 读写这些任务，程序逻辑负责实际的 CRUD。
 */
public final class TaskStore {

    private final Database db;

    public TaskStore(Database db) {
        this.db = db;
    }

    public void insert(Task task) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO task (id, title, description, status, created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, task.id());
            ps.setString(2, task.title());
            ps.setString(3, task.description());
            ps.setString(4, task.status());
            ps.setString(5, task.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("插入任务失败: " + task.id(), e);
        }
    }

    public Optional<Task> findById(String id) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, title, description, status, created_at FROM task WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(fromRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("查询任务失败: " + id, e);
        }
    }

    public List<Task> findAll() {
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, title, description, status, created_at FROM task ORDER BY created_at")) {
            List<Task> tasks = new ArrayList<>();
            while (rs.next()) {
                tasks.add(fromRow(rs));
            }
            return tasks;
        } catch (SQLException e) {
            throw new IllegalStateException("查询全部任务失败", e);
        }
    }

    private Task fromRow(ResultSet rs) throws SQLException {
        return new Task(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("created_at"));
    }
}