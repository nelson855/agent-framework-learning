package com.example.agentlearning.stage02;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/**
 * Agent 运行状态（Agent State）的存取，对应 {@code agent_run} 表。
 *
 * <p>与 message 表不同：这里记录的是<b>执行状态机</b>——goal 是什么、现在
 * RUNNING / WAITING_TOOL / COMPLETED / FAILED、计划走到第几步。
 * 它回答的问题是"这个任务干到哪了"，而不是"聊了什么"。
 */
public final class AgentRunRepository {

    private final Database db;

    public AgentRunRepository(Database db) {
        this.db = db;
    }

    public AgentRun create(String runId, String conversationId, String goal) {
        String now = Instant.now().toString();
        AgentRun run = new AgentRun(runId, conversationId, goal, RunStatus.RUNNING, 0, now, now);
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO agent_run (run_id, conversation_id, goal, status, current_step, started_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, run.runId());
            ps.setString(2, run.conversationId());
            ps.setString(3, run.goal());
            ps.setString(4, run.status().name());
            ps.setInt(5, run.currentStep());
            ps.setString(6, run.startedAt());
            ps.setString(7, run.updatedAt());
            ps.executeUpdate();
            return run;
        } catch (SQLException e) {
            throw new IllegalStateException("创建 Agent 运行失败", e);
        }
    }

    public AgentRun updateStatus(String runId, RunStatus status, int currentStep) {
        String now = Instant.now().toString();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE agent_run SET status = ?, current_step = ?, updated_at = ? WHERE run_id = ?")) {
            ps.setString(1, status.name());
            ps.setInt(2, currentStep);
            ps.setString(3, now);
            ps.setString(4, runId);
            ps.executeUpdate();
            return findById(runId).orElseThrow(() -> new IllegalStateException("运行不存在: " + runId));
        } catch (SQLException e) {
            throw new IllegalStateException("更新运行状态失败: " + runId, e);
        }
    }

    public Optional<AgentRun> findById(String runId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT run_id, conversation_id, goal, status, current_step, started_at, updated_at "
                        + "FROM agent_run WHERE run_id = ?")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(fromRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("查询运行状态失败: " + runId, e);
        }
    }

    public Optional<AgentRun> findLatestByConversation(String conversationId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT run_id, conversation_id, goal, status, current_step, started_at, updated_at "
                        + "FROM agent_run WHERE conversation_id = ? ORDER BY updated_at DESC LIMIT 1")) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(fromRow(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("查询运行状态失败: " + conversationId, e);
        }
    }

    private AgentRun fromRow(ResultSet rs) throws SQLException {
        return new AgentRun(
                rs.getString("run_id"),
                rs.getString("conversation_id"),
                rs.getString("goal"),
                RunStatus.valueOf(rs.getString("status")),
                rs.getInt("current_step"),
                rs.getString("started_at"),
                rs.getString("updated_at"));
    }
}