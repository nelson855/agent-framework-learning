package com.example.agentlearning.stage03;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户长期记忆的存取，对应 {@code memory} 表（关于用户偏好/事实/约定）。
 */
public final class MemoryRepository {

    private final Database db;

    public MemoryRepository(Database db) {
        this.db = db;
    }

    public void save(String userId, String type, String content, int importance) {
        String id = "mem-" + Math.abs(content.hashCode());
        String sql = """
                INSERT INTO memory (id, user_id, type, content, importance, created_at, last_used_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET last_used_at = excluded.last_used_at""";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, userId);
            ps.setString(3, type);
            ps.setString(4, content);
            ps.setInt(5, importance);
            ps.setString(6, Instant.now().toString());
            ps.setString(7, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("保存记忆失败", e);
        }
    }

    public List<Memory> search(List<String> keywords, String type, int limit) {
        List<Memory> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, type, content, importance, created_at, last_used_at FROM memory WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (keywords != null && !keywords.isEmpty()) {
            sql.append(" AND (");
            for (String keyword : keywords) {
                sql.append(" content LIKE ? OR");
                params.add("%" + keyword + "%");
            }
            sql.setLength(sql.length() - 2);
            sql.append(")");
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND type = ?");
            params.add(type);
        }
        sql.append(" ORDER BY importance DESC, COALESCE(last_used_at, created_at) DESC LIMIT ?");
        params.add(limit);

        try (PreparedStatement ps = db.connection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(fromRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("检索记忆失败", e);
        }
    }

    public void touch(String id) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE memory SET last_used_at = ? WHERE id = ?")) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新记忆使用时间失败", e);
        }
    }

    public List<Memory> findAll() {
        List<Memory> result = new ArrayList<>();
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, user_id, type, content, importance, created_at, last_used_at FROM memory")) {
            while (rs.next()) {
                result.add(fromRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("列出记忆失败", e);
        }
    }

    public void deleteAll() {
        try (Statement st = db.connection().createStatement()) {
            st.executeUpdate("DELETE FROM memory");
        } catch (SQLException e) {
            throw new IllegalStateException("清空记忆失败", e);
        }
    }

    private Memory fromRow(ResultSet rs) throws SQLException {
        return new Memory(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("type"),
                rs.getString("content"),
                rs.getInt("importance"),
                rs.getString("created_at"),
                rs.getString("last_used_at"));
    }
}