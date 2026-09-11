package com.example.agentlearning.stage04;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * RunStore（StateStore）：持久化 run 行（goal / status / current_step / plan_json）。
 *
 * <p>plan_json 存储 Planner 输出的 PlanStep 列表（JSON 数组），Web 反序列化后展示。
 */
public final class RunStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Database db;

    public RunStore(Database db) {
        this.db = db;
    }

    /** 创建一次新 run（状态 RUNNING，current_step=0）。 */
    public void create(String runId, String goal, String planJson, long startedAt) {
        try (PreparedStatement ps = db.connection().prepareStatement("""
                INSERT INTO run (run_id, goal, status, current_step, plan_json, started_at)
                VALUES (?, ?, ?, 0, ?, ?)""")) {
            ps.setString(1, runId);
            ps.setString(2, goal);
            ps.setString(3, RunStatus.RUNNING.name());
            ps.setString(4, planJson);
            ps.setLong(5, startedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("创建 run 失败: " + runId, e);
        }
    }

    /** 更新 run 的状态与当前步数。 */
    public void update(String runId, RunStatus status, int currentStep) {
        try (PreparedStatement ps = db.connection().prepareStatement("""
                UPDATE run SET status = ?, current_step = ? WHERE run_id = ?""")) {
            ps.setString(1, status.name());
            ps.setInt(2, currentStep);
            ps.setString(3, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新 run 失败: " + runId, e);
        }
    }

    /** 结束 run（写结束状态与结束时间）。 */
    public void finish(String runId, RunStatus status) {
        try (PreparedStatement ps = db.connection().prepareStatement("""
                UPDATE run SET status = ?, ended_at = ? WHERE run_id = ?""")) {
            ps.setString(1, status.name());
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("结束 run 失败: " + runId, e);
        }
    }

    /** 按 run_id 查询 run。 */
    public RunRow findByRunId(String runId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT run_id, goal, status, current_step, plan_json, started_at, ended_at " +
                        "FROM run WHERE run_id = ?")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Long ended = rs.getObject("ended_at") == null ? null : rs.getLong("ended_at");
                return new RunRow(rs.getString("run_id"), rs.getString("goal"),
                        RunStatus.valueOf(rs.getString("status")),
                        rs.getInt("current_step"), rs.getString("plan_json"),
                        rs.getLong("started_at"), ended);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询 run 失败: " + runId, e);
        }
    }

    /** 所有 run（按创建顺序）。 */
    public java.util.List<RunRow> all() {
        java.util.List<RunRow> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT run_id, goal, status, current_step, plan_json, started_at, ended_at " +
                        "FROM run ORDER BY id ASC");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Long ended = rs.getObject("ended_at") == null ? null : rs.getLong("ended_at");
                out.add(new RunRow(rs.getString("run_id"), rs.getString("goal"),
                        RunStatus.valueOf(rs.getString("status")),
                        rs.getInt("current_step"), rs.getString("plan_json"),
                        rs.getLong("started_at"), ended));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询全部 run 失败", e);
        }
        return out;
    }

    public record RunRow(String runId, String goal, RunStatus status,
                         int currentStep, String planJson, long startedAt, Long endedAt) {
    }
}