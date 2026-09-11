package com.example.agentlearning.stage04;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * CheckpointStore：保存/恢复 Agent 运行的上下文快照（消息历史）。
 *
 * <p>Agent 每走一步都保存 Checkpoint（序列化后的 List&lt;Message&gt;），
 * 当人工批准后或模拟中断后可以从最新的 Checkpoint 恢复上下文。
 */
public final class CheckpointStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Database db;

    public CheckpointStore(Database db) {
        this.db = db;
    }

    /** 保存一次 Checkpoint，返回版本号。 */
    public int save(String runId, int stepIndex, List<Message> context) {
        try (PreparedStatement ps = db.connection().prepareStatement("""
                INSERT INTO checkpoint (run_id, step_index, context_json, saved_at)
                VALUES (?, ?, ?, ?)""")) {
            ps.setString(1, runId);
            ps.setInt(2, stepIndex);
            ps.setString(3, toJson(context));
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet ks = ps.getGeneratedKeys()) {
                return ks.next() ? ks.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("保存 checkpoint 失败: " + runId, e);
        }
    }

    /** 取回指定 run 的最新 Checkpoint（按 id DESC）。 */
    public Optional<List<Message>> loadLatest(String runId) {
        try (PreparedStatement ps = db.connection().prepareStatement("""
                SELECT context_json FROM checkpoint WHERE run_id = ?
                ORDER BY id DESC LIMIT 1""")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(fromJson(rs.getString("context_json")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("加载 checkpoint 失败: " + runId, e);
        }
    }

    private static String toJson(List<Message> ctx) {
        try {
            return MAPPER.writeValueAsString(ctx);
        } catch (IOException e) {
            throw new IllegalStateException("序列化 context 失败", e);
        }
    }

    private static List<Message> fromJson(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("反序列化 context 失败", e);
        }
    }
}