package com.example.agentlearning.stage03;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 评估/校验结果的存取，对应 {@code evaluation} 表。
 * 每次迭代一行，可看到「Validator 失败 → 重试 → Evaluator 通过」的过程。
 */
public final class EvaluationRepository {

    private final Database db;

    public EvaluationRepository(Database db) {
        this.db = db;
    }

    public record EvaluationEntry(
            String runId,
            int iteration,
            boolean validatorPass,
            List<String> validatorErrors,
            boolean evaluatorPass,
            int evaluatorScore,
            List<String> evaluatorIssues,
            String reportText) {
    }

    public void save(EvaluationEntry entry) {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = db.connection().prepareStatement("""
                INSERT INTO evaluation
                    (id, run_id, iteration, validator_pass, validator_errors,
                     evaluator_pass, evaluator_score, evaluator_issues, report_text, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setString(1, id);
            ps.setString(2, entry.runId());
            ps.setInt(3, entry.iteration());
            ps.setInt(4, entry.validatorPass() ? 1 : 0);
            ps.setString(5, String.join("; ", entry.validatorErrors()));
            ps.setInt(6, entry.evaluatorPass() ? 1 : 0);
            ps.setInt(7, entry.evaluatorScore());
            ps.setString(8, String.join("; ", entry.evaluatorIssues()));
            ps.setString(9, entry.reportText());
            ps.setString(10, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("保存评估结果失败: " + entry.runId(), e);
        }
    }

    public List<EvaluationEntry> list(String runId) {
        List<EvaluationEntry> result = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT * FROM evaluation WHERE run_id = ? ORDER BY iteration ASC")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new EvaluationEntry(
                            rs.getString("run_id"),
                            rs.getInt("iteration"),
                            rs.getInt("validator_pass") == 1,
                            split(rs.getString("validator_errors")),
                            rs.getInt("evaluator_pass") == 1,
                            rs.getInt("evaluator_score"),
                            split(rs.getString("evaluator_issues")),
                            rs.getString("report_text")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("列出评估结果失败: " + runId, e);
        }
    }

    private static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : text.split(";")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }
}