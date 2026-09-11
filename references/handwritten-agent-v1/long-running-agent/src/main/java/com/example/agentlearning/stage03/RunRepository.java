package com.example.agentlearning.stage03;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 运行实例的存取，对应 {@code run} 表。
 */
public final class RunRepository {

    private final Database db;

    public RunRepository(Database db) {
        this.db = db;
    }

    /** 创建一条运行实例。 */
    public Run create(String goal) {
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO run (id, goal, status, current_step, started_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, goal);
            ps.setString(3, RunStatus.RUNNING.name());
            ps.setInt(4, 0);
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
            return new Run(id, goal, RunStatus.RUNNING, 0);
        } catch (SQLException e) {
            throw new IllegalStateException("创建运行失败", e);
        }
    }

    public Optional<Run> findById(String runId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id, goal, status, current_step FROM run WHERE id = ?")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(fromRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("读取运行失败: " + runId, e);
        }
    }

    public void updateStatus(String runId, RunStatus status) {
        update(runId, status, null);
    }

    public void updateCurrentStep(String runId, int currentStep) {
        update(runId, null, currentStep);
    }

    public void update(String runId, RunStatus status, Integer currentStep) {
        StringBuilder sql = new StringBuilder("UPDATE run SET ");
        boolean first = true;
        if (status != null) {
            sql.append("status = ?");
            first = false;
        }
        if (currentStep != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("current_step = ?");
        }
        sql.append(" WHERE id = ?");
        try (PreparedStatement ps = db.connection().prepareStatement(sql.toString())) {
            int i = 1;
            if (status != null) {
                ps.setString(i++, status.name());
            }
            if (currentStep != null) {
                ps.setInt(i++, currentStep);
            }
            ps.setString(i, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新运行失败: " + runId, e);
        }
    }

    private Run fromRow(ResultSet rs) throws SQLException {
        RunStatus status;
        try {
            status = RunStatus.valueOf(rs.getString("status"));
        } catch (IllegalArgumentException e) {
            status = RunStatus.PENDING;
        }
        return new Run(rs.getString("id"), rs.getString("goal"), status, rs.getInt("current_step"));
    }
}