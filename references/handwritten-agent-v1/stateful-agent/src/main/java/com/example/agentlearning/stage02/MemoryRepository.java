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
 * 长期记忆（Long-term Memory）的存取，对应 {@code memory} 表。
 *
 * <p>关键设计：这里只存"经过选择"的信息，并且按 {@code importance} / 最近使用
 * （{@code last_used_at}）组织，方便检索时先给最有用、最新的记忆。
 * 不使用向量库，检索靠 keyword LIKE + type 过滤（教学目的）。
 */
public final class MemoryRepository {

    private final Database db;

    public MemoryRepository(Database db) {
        this.db = db;
    }

    public Memory save(String userId, String type, String content, int importance) {
        String id = "mem-" + UUID.randomUUID().toString().substring(0, 8);
        Memory memory = new Memory(id, userId, type, content, importance, Instant.now().toString(), null);
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO memory (id, user_id, type, content, importance, created_at, last_used_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, memory.id());
            ps.setString(2, memory.userId());
            ps.setString(3, memory.type());
            ps.setString(4, memory.content());
            ps.setInt(5, memory.importance());
            ps.setString(6, memory.createdAt());
            ps.setString(7, memory.lastUsedAt());
            ps.executeUpdate();
            return memory;
        } catch (SQLException e) {
            throw new IllegalStateException("保存记忆失败", e);
        }
    }

    public List<Memory> search(List<String> keywords, String type, int limit) {
        List<Memory> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, type, content, importance, created_at, last_used_at FROM memory WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (type != null && !type.isBlank()) {
            sql.append(" AND type = ?");
            params.add(type);
        }
        if (!keywords.isEmpty()) {
            sql.append(" AND (");
            for (String keyword : keywords) {
                sql.append(" content LIKE ? OR");
                params.add("%" + keyword + "%");
            }
            sql.setLength(sql.length() - 2);
            sql.append(")");
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

    public List<Memory> findAll() {
        List<Memory> result = new ArrayList<>();
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, user_id, type, content, importance, created_at, last_used_at "
                                + "FROM memory ORDER BY importance DESC, created_at")) {
            while (rs.next()) {
                result.add(fromRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("列出记忆失败", e);
        }
    }

    public void touch(String id) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE memory SET last_used_at = ? WHERE id = ?")) {
            ps.setString(1, Instant.now().toString());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新记忆最近使用时间失败: " + id, e);
        }
    }

    public Optional<Memory> findById(String id) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, user_id, type, content, importance, created_at, last_used_at "
                        + "FROM memory WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(fromRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("查询记忆失败: " + id, e);
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