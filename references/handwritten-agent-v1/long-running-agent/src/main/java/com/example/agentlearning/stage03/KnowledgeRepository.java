package com.example.agentlearning.stage03;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识文档的存取，对应 {@code knowledge_doc} 表（关于项目/外部世界）。
 * 检索 = keyword(title/content LIKE) + tags。
 */
public final class KnowledgeRepository {

    private final Database db;

    public KnowledgeRepository(Database db) {
        this.db = db;
    }

    public void insert(KnowledgeDoc doc) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO knowledge_doc (id, title, content, tags, created_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, doc.id());
            ps.setString(2, doc.title());
            ps.setString(3, doc.content());
            ps.setString(4, doc.tags());
            ps.setString(5, doc.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("插入知识文档失败: " + doc.id(), e);
        }
    }

    public List<KnowledgeDoc> search(List<String> keywords, List<String> tags, int limit) {
        List<KnowledgeDoc> result = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, content, tags, created_at FROM knowledge_doc WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (!keywords.isEmpty()) {
            sql.append(" AND (");
            for (String keyword : keywords) {
                sql.append(" title LIKE ? OR content LIKE ? OR");
                params.add("%" + keyword + "%");
                params.add("%" + keyword + "%");
            }
            sql.setLength(sql.length() - 2);
            sql.append(")");
        }
        if (!tags.isEmpty()) {
            sql.append(" AND (");
            for (String tag : tags) {
                sql.append(" tags LIKE ? OR");
                params.add("%" + tag + "%");
            }
            sql.setLength(sql.length() - 2);
            sql.append(")");
        }
        sql.append(" ORDER BY created_at LIMIT ?");
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
            throw new IllegalStateException("检索知识文档失败", e);
        }
    }

    public List<KnowledgeDoc> findAll() {
        List<KnowledgeDoc> result = new ArrayList<>();
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT id, title, content, tags, created_at FROM knowledge_doc ORDER BY created_at")) {
            while (rs.next()) {
                result.add(fromRow(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("列出知识文档失败", e);
        }
    }

    public int count() {
        try (Statement st = db.connection().createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM knowledge_doc")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("统计知识文档失败", e);
        }
    }

    public void deleteAll() {
        try (Statement st = db.connection().createStatement()) {
            st.executeUpdate("DELETE FROM knowledge_doc");
        } catch (SQLException e) {
            throw new IllegalStateException("清空知识文档失败", e);
        }
    }

    private KnowledgeDoc fromRow(ResultSet rs) throws SQLException {
        return new KnowledgeDoc(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("tags"),
                rs.getString("created_at"));
    }
}