package com.example.agentlearning.stage03;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 压缩摘要的存取，对应 {@code compaction_summary} 表。
 * 每次压缩新增一个版本（不覆盖历史），Resume 时取最新。
 */
public final class CompactionSummaryRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Database db;

    public CompactionSummaryRepository(Database db) {
        this.db = db;
    }

    public int save(String runId, CompactionSummary summary) {
        String id = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO compaction_summary (id, run_id, version, summary_json, created_at)
                VALUES (?, ?, ?, ?, ?)""";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, runId);
            ps.setInt(3, summary.version());
            ps.setString(4, toJson(summary));
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
            return summary.version();
        } catch (SQLException e) {
            throw new IllegalStateException("保存 CompactionSummary 失败: " + runId, e);
        }
    }

    public List<CompactionSummary> listSummaries(String runId) {
        List<CompactionSummary> result = new ArrayList<>();
        String sql = """
                SELECT summary_json FROM compaction_summary
                WHERE run_id = ? ORDER BY version ASC""";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(fromJson(rs.getString("summary_json")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("列出 CompactionSummary 失败: " + runId, e);
        }
    }

    /** 返回所有版本的紧凑文本描述。 */
    public List<String> renderAll(String runId) {
        List<String> lines = new ArrayList<>();
        for (CompactionSummary s : listSummaries(runId)) {
            lines.add("[v" + s.version() + "] goal=" + s.goal()
                    + " | completed=" + s.completed()
                    + " | pendingActions=" + s.pendingActions());
        }
        return lines;
    }

    private static String summaryToText(CompactionSummary s) {
        StringBuilder sb = new StringBuilder();
        sb.append("GOAL: ").append(s.goal()).append('\n');
        sb.append("COMPLETED: ").append(s.completed()).append('\n');
        sb.append("IMPORTANT_FACTS: ").append(s.importantFacts()).append('\n');
        sb.append("DECISIONS: ").append(s.decisions()).append('\n');
        sb.append("PENDING_ACTIONS: ").append(s.pendingActions()).append('\n');
        return sb.toString();
    }

    /** 供 ContextBuilder 使用：最新摘要的纯文本形式。 */
    public String latestSummaryText(String runId) {
        List<CompactionSummary> all = listSummaries(runId);
        if (all.isEmpty()) {
            return null;
        }
        return summaryToText(all.get(all.size() - 1));
    }

    private static String toJson(CompactionSummary summary) {
        try {
            return MAPPER.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化 CompactionSummary 失败", e);
        }
    }

    private static CompactionSummary fromJson(String json) {
        try {
            return MAPPER.readValue(json, CompactionSummary.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("反序列化 CompactionSummary 失败: " + json, e);
        }
    }
}