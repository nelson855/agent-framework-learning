package com.example.springai.stateful.domain;

import java.sql.Connection;
import java.sql.DriverManager;
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
 * 计划持久化，SQLite 直连。纯业务代码，不感知 AI。
 *
 * <p>@FW-CMP [STATE] 计划与状态框架不管，只能自建
 *   手写（手写版 PlanStore / StatefulAgentRunner 里的步骤推进）：
 *     // plan + plan_step 两张表，步骤状态自己 update
 *   框架（本类）：
 *     // Spring AI 没有"计划"这个概念，表结构和推进逻辑原样自建
 *   差异：框架只给到"单次对话 + 工具循环"这一层，"多步计划怎么拆、
 *   走到哪一步了、失败了怎么办"全是业务自己的表和代码。
 *   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#4-plan-state
 */
public class PlanRepository implements AutoCloseable {

    private final Connection connection;

    public PlanRepository(String jdbcUrl) {
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS plan (
                            plan_id TEXT PRIMARY KEY,
                            run_id TEXT NOT NULL,
                            created_at INTEGER NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS plan_step (
                            plan_id TEXT NOT NULL,
                            step_id TEXT NOT NULL,
                            seq INTEGER NOT NULL,
                            description TEXT NOT NULL,
                            status TEXT NOT NULL,
                            failure_reason TEXT,
                            PRIMARY KEY (plan_id, step_id),
                            FOREIGN KEY (plan_id) REFERENCES plan(plan_id)
                        )
                        """);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("open plan database failed: " + jdbcUrl, e);
        }
    }

    /** 内存库，测试用。 */
    public static PlanRepository inMemory() {
        return new PlanRepository("jdbc:sqlite::memory:");
    }

    public synchronized Plan createPlan(String runId, List<String> descriptions) {
        String planId = UUID.randomUUID().toString();
        try (PreparedStatement planStatement = connection.prepareStatement(
                     "INSERT INTO plan (plan_id, run_id, created_at) VALUES (?, ?, ?)");
             PreparedStatement stepStatement = connection.prepareStatement(
                     "INSERT INTO plan_step (plan_id, step_id, seq, description, status) "
                             + "VALUES (?, ?, ?, ?, ?)")) {
            planStatement.setString(1, planId);
            planStatement.setString(2, runId);
            planStatement.setLong(3, Instant.now().toEpochMilli());
            planStatement.executeUpdate();
            List<PlanStep> steps = new ArrayList<>();
            for (int i = 0; i < descriptions.size(); i++) {
                String stepId = UUID.randomUUID().toString();
                stepStatement.setString(1, planId);
                stepStatement.setString(2, stepId);
                stepStatement.setInt(3, i);
                stepStatement.setString(4, descriptions.get(i));
                stepStatement.setString(5, PlanStepStatus.PENDING.name());
                stepStatement.executeUpdate();
                steps.add(new PlanStep(stepId, planId, i, descriptions.get(i), PlanStepStatus.PENDING));
            }
            return new Plan(planId, runId, steps);
        } catch (SQLException e) {
            throw new IllegalStateException("create plan failed", e);
        }
    }

    public synchronized Optional<Plan> findById(String planId) {
        try (PreparedStatement planStatement = connection.prepareStatement(
                "SELECT plan_id, run_id FROM plan WHERE plan_id = ?")) {
            planStatement.setString(1, planId);
            try (ResultSet planRows = planStatement.executeQuery()) {
                if (!planRows.next()) {
                    return Optional.empty();
                }
                String runId = planRows.getString("run_id");
                List<PlanStep> steps = new ArrayList<>();
                try (PreparedStatement stepStatement = connection.prepareStatement(
                        "SELECT step_id, seq, description, status FROM plan_step "
                                + "WHERE plan_id = ? ORDER BY seq")) {
                    stepStatement.setString(1, planId);
                    try (ResultSet stepRows = stepStatement.executeQuery()) {
                        while (stepRows.next()) {
                            steps.add(new PlanStep(
                                    stepRows.getString("step_id"),
                                    planId,
                                    stepRows.getInt("seq"),
                                    stepRows.getString("description"),
                                    PlanStepStatus.valueOf(stepRows.getString("status"))));
                        }
                    }
                }
                return Optional.of(new Plan(planId, runId, steps));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("find plan failed: " + planId, e);
        }
    }

    /** 查一次运行产生过的所有计划（原计划 + 每次重规划的新计划），按创建时间排序。 */
    public synchronized List<Plan> findByRunId(String runId) {
        List<Plan> history = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT plan_id FROM plan WHERE run_id = ? ORDER BY created_at")) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    findById(rows.getString("plan_id")).ifPresent(history::add);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("find plans failed for run: " + runId, e);
        }
        return history;
    }

    public synchronized void updateStepStatus(
            String planId, String stepId, PlanStepStatus status, String failureReason) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE plan_step SET status = ?, failure_reason = ? WHERE plan_id = ? AND step_id = ?")) {
            statement.setString(1, status.name());
            statement.setString(2, failureReason);
            statement.setString(3, planId);
            statement.setString(4, stepId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("plan step not found: " + planId + "/" + stepId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("update plan step failed: " + planId + "/" + stepId, e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("close plan database failed", e);
        }
    }
}
