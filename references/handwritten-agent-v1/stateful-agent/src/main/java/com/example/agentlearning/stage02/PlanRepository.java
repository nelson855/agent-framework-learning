package com.example.agentlearning.stage02;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 计划（Plan）的存取，对应 {@code plan} + {@code plan_step} 表。
 *
 * <p>一份计划挂在一次 {@code agent_run} 下，steps 存在独立的 {@code plan_step} 表。
 * 与 message 表不同：这里存的是"任务要分几步、每步做到哪"的结构化执行蓝图。
 * 重规划时生成新的一条 plan + 一批 plan_step（保留已完成步骤的状态）。
 */
public final class PlanRepository {

    private final Database db;

    public PlanRepository(Database db) {
        this.db = db;
    }

    /** 保存一份完整的计划（含所有步骤）。 */
    public void save(String runId, Plan plan) {
        String planId = "plan-" + UUID.randomUUID().toString().substring(0, 8);
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO plan (id, run_id, goal, created_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, planId);
            ps.setString(2, runId);
            ps.setString(3, plan.goal());
            ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("保存计划失败", e);
        }

        int stepNo = 1;
        for (PlanStep step : plan.steps()) {
            saveStep(planId, stepNo++, step);
        }
    }

    private void saveStep(String planId, int stepNo, PlanStep step) {
        String id = "ps-" + UUID.randomUUID().toString().substring(0, 8);
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO plan_step (id, plan_id, step_no, description, status, failure_reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, planId);
            ps.setInt(3, stepNo);
            ps.setString(4, step.description());
            ps.setString(5, step.status().name());
            ps.setString(6, step.failureReason());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("保存计划步骤失败: " + step.id(), e);
        }
    }

    /** 更新最新计划中所有步骤的状态（按 step_no 匹配）。 */
    public void updateStepStatuses(Plan plan, String runId) {
        String planId = findLatestPlanId(runId);
        if (planId == null) {
            return;
        }
        int stepNo = 1;
        for (PlanStep step : plan.steps()) {
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "UPDATE plan_step SET status = ?, failure_reason = ? "
                            + "WHERE plan_id = ? AND step_no = ?")) {
                ps.setString(1, step.status().name());
                ps.setString(2, step.failureReason());
                ps.setString(3, planId);
                ps.setInt(4, stepNo++);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("更新计划步骤失败: " + step.id(), e);
            }
        }
    }

    /** 查询某次运行对应的最新一份计划（含步骤）。 */
    public Optional<Plan> findLatestByRun(String runId) {
        String planId = findLatestPlanId(runId);
        if (planId == null) {
            return Optional.empty();
        }
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT goal FROM plan WHERE id = ?")) {
            ps.setString(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(loadPlan(planId, rs.getString("goal")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询计划失败: " + runId, e);
        }
        return Optional.empty();
    }

    private String findLatestPlanId(String runId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id FROM plan WHERE run_id = ? ORDER BY created_at DESC LIMIT 1")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("id");
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询计划 id 失败: " + runId, e);
        }
        return null;
    }

    private Plan loadPlan(String planId, String goal) throws SQLException {
        List<PlanStep> steps = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT step_no, description, status, failure_reason FROM plan_step "
                        + "WHERE plan_id = ? ORDER BY step_no")) {
            ps.setString(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    steps.add(new PlanStep(
                            "S" + rs.getInt("step_no"),
                            rs.getString("description"),
                            PlanStepStatus.valueOf(rs.getString("status")),
                            rs.getString("failure_reason")));
                }
            }
        }
        return new Plan(goal, steps);
    }
}