package com.example.agentlearning.stage03;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 上下文快照的存取，对应 {@code context_snapshot} 表。
 */
public final class ContextSnapshotRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Database db;

    public ContextSnapshotRepository(Database db) {
        this.db = db;
    }

    public void save(ContextSnapshot snapshot) {
        String sql = """
                INSERT INTO context_snapshot
                    (id, run_id, step_index, rag_docs_json, memories_json, selected_context, compacted_summary, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, snapshot.id());
            ps.setString(2, snapshot.runId());
            ps.setInt(3, snapshot.stepIndex());
            ps.setString(4, toJsonDocList(snapshot.ragDocs()));
            ps.setString(5, toJsonMemoryList(snapshot.memories()));
            ps.setString(6, snapshot.selectedContext());
            ps.setString(7, snapshot.compactedSummary());
            ps.setString(8, snapshot.createdAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("保存 ContextSnapshot 失败: " + snapshot.runId(), e);
        }
    }

    /** 返回一张只含{@code title}与{@code content}的文档快照，用于观察。 */
    public List<DocSummary> listRagDocs(String runId) {
        List<DocSummary> result = new ArrayList<>();
        String sql = "SELECT rag_docs_json FROM context_snapshot WHERE run_id = ? ORDER BY step_index ASC";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (first) {
                        String json = rs.getString("rag_docs_json");
                        if (json != null && !json.isBlank()) {
                            result.addAll(parseDocSummaryList(json));
                        }
                        first = false;
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("读取 RAG 文档快照失败: " + runId, e);
        }
    }

    /** 返回一张只含最重要的记忆快照。 */
    public List<Memory> listMemories(String runId) {
        List<Memory> result = new ArrayList<>();
        String sql = "SELECT memories_json FROM context_snapshot WHERE run_id = ? ORDER BY step_index ASC";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (first) {
                        String json = rs.getString("memories_json");
                        if (json != null && !json.isBlank()) {
                            result.addAll(parseMemoryList(json));
                        }
                        first = false;
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("读取记忆快照失败: " + runId, e);
        }
    }

    /** 折叠展示全部快照；返回按 step 排序的轻量描述。 */
    public List<SimpleSnapshot> listSnapshots(String runId) {
        List<SimpleSnapshot> result = new ArrayList<>();
        String sql = """
                SELECT step_index, selected_context, compacted_summary, created_at
                FROM context_snapshot WHERE run_id = ? ORDER BY step_index ASC""";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StringBuilder selected = new StringBuilder(rs.getString("selected_context") == null
                            ? "" : rs.getString("selected_context"));
                    String compacted = rs.getString("compacted_summary");
                    if (compacted != null && !compacted.isBlank()) {
                        selected.append("\n[COMPACTED SUMMARY] ").append(compacted);
                    }
                    result.add(new SimpleSnapshot(rs.getInt("step_index"), selected.toString(), rs.getString("created_at")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("列出 ContextSnapshot 失败: " + runId, e);
        }
    }

    private static String toJsonDocList(List<KnowledgeDoc> docs) {
        try {
            return MAPPER.writeValueAsString(docs.stream().map(d -> new DocSummary(d.title(), d.content())).toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 RAG 文档失败", e);
        }
    }

    private static String toJsonMemoryList(List<Memory> memories) {
        try {
            return MAPPER.writeValueAsString(memories);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化记忆失败", e);
        }
    }

    private static List<DocSummary> parseDocSummaryList(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<List<DocSummary>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("反序列化 RAG 文档失败", e);
        }
    }

    private static List<Memory> parseMemoryList(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<List<Memory>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("反序列化记忆失败", e);
        }
    }

    /** 一份轻量的文档快照。 */
    public record DocSummary(String title, String content) {
    }

    /** 一份折叠后的上下文快照。 */
    public record SimpleSnapshot(int stepIndex, String context, String createdAt) {
    }
}