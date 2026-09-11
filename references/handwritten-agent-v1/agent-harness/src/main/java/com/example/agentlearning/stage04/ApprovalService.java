package com.example.agentlearning.stage04;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ApprovalService：高风险工具的人工审批队列。
 *
 * <p>持久化到 approval_request 表。AgentRunner 在 deleteTask 到达执行层前调用
 * {@link #create} 生成 PENDING 请求并暂停 run；人工通过 {@link #approve}/{@link #reject}
 * 决定后，AgentRunner 才真正执行或取消。审批本身不可先斩后奏。
 */
public final class ApprovalService {

    private final Database db;
    private int idSeq = 0; // 与 DB 自增 id 保持一致（create 时读回）

    public ApprovalService(Database db) {
        this.db = db;
    }

    /** 创建一条待审批请求，返回新请求 id。 */
    public int create(String runId, ToolCall call, String reason, String risk) {
        String toolCallJson = ToolJson.toJson(call);
        try (PreparedStatement ps = db.connection().prepareStatement("""
                INSERT INTO approval_request (run_id, tool_call_json, reason, risk, status, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?)""")) {
            ps.setString(1, runId);
            ps.setString(2, toolCallJson);
            ps.setString(3, reason);
            ps.setString(4, risk);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet ks = ps.getGeneratedKeys()) {
                return ks.next() ? ks.getInt(1) : (idSeq = idSeq + 1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("创建审批失败: " + runId, e);
        }
    }

    /** 批准 → status=APPROVED。 */
    public void approve(int id) {
        setIdStatus(id, "APPROVED");
    }

    /** 拒绝 → status=REJECTED。 */
    public void reject(int id) {
        setIdStatus(id, "REJECTED");
    }

    private void setIdStatus(int id, String status) {
        try (PreparedStatement ps = db.connection().prepareStatement("""
                UPDATE approval_request SET status = ?, decided_at = ? WHERE id = ?""")) {
            ps.setString(1, status);
            ps.setLong(2, System.currentTimeMillis());
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("更新审批状态失败: " + id, e);
        }
    }

    public Optional<ApprovalRequest> findById(int id) {
        try (PreparedStatement ps = db.connection().prepareStatement("""
                SELECT id, run_id, tool_call_json, reason, risk, status, created_at, decided_at
                FROM approval_request WHERE id = ?""")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(row(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询审批失败: " + id, e);
        }
    }

    public List<ApprovalRequest> all() {
        List<ApprovalRequest> out = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement("""
                SELECT id, run_id, tool_call_json, reason, risk, status, created_at, decided_at
                FROM approval_request ORDER BY id ASC""");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(row(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询全部审批失败", e);
        }
        return out;
    }

    public List<ApprovalRequest> pendingByRun(String runId) {
        List<ApprovalRequest> out = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement("""
                SELECT id, run_id, tool_call_json, reason, risk, status, created_at, decided_at
                FROM approval_request WHERE run_id = ? AND status = 'PENDING' ORDER BY id ASC""")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(row(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询待审批失败: " + runId, e);
        }
        return out;
    }

    private static ApprovalRequest row(ResultSet rs) throws SQLException {
        Long decided = rs.getObject("decided_at") == null ? null : rs.getLong("decided_at");
        return new ApprovalRequest(rs.getInt("id"), rs.getString("run_id"),
                rs.getString("tool_call_json"), rs.getString("reason"), rs.getString("risk"),
                ApprovalStatus.valueOf(rs.getString("status")),
                rs.getLong("created_at"), decided);
    }

    public enum ApprovalStatus { PENDING, APPROVED, REJECTED }

    public record ApprovalRequest(int id, String runId, String toolCallJson,
                                  String reason, String risk, ApprovalStatus status,
                                  long createdAt, Long decidedAt) {
        public boolean isPending() {
            return status == ApprovalStatus.PENDING;
        }
    }
}