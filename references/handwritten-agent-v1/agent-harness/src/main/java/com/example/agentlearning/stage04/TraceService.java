package com.example.agentlearning.stage04;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * TraceService：记录并查询 Agent 运行的可观察事件（时间线）与工具调用明细。
 *
 * <p>把所有事件持久化到 SQLite，供 Web 的 Trace Timeline / Tool Events 面板与测试断言查询。
 */
public final class TraceService {

    private final Database db;

    public TraceService(Database db) {
        this.db = db;
    }

    /** 记录一条 Trace 事件。 */
    public void record(TraceEvent e) {
        try (PreparedStatement ps = db.connection().prepareStatement("""
                INSERT INTO trace_event (run_id, worker_id, type, message, data_json, at)
                VALUES (?, ?, ?, ?, ?, ?)""")) {
            ps.setString(1, e.runId());
            ps.setString(2, e.workerId());
            ps.setString(3, e.type().name());
            ps.setString(4, e.message());
            ps.setString(5, e.dataJson());
            ps.setLong(6, e.at());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("写入 trace 失败: " + e, ex);
        }
    }

    /** 记录一条工具调用明细。 */
    public void recordTool(ToolEvent e) {
        try (PreparedStatement ps = db.connection().prepareStatement("""
                INSERT INTO tool_event (run_id, worker_id, tool, args, success, result, elapsed_ms, at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setString(1, e.runId());
            ps.setString(2, e.workerId());
            ps.setString(3, e.tool());
            ps.setString(4, e.args());
            ps.setInt(5, e.success() ? 1 : 0);
            ps.setString(6, e.result());
            ps.setLong(7, e.elapsedMs());
            ps.setLong(8, e.at());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("写入 tool_event 失败: " + e, ex);
        }
    }

    /** 某次 run 的完整事件时间线（按写入顺序）。 */
    public List<TraceEvent> eventsFor(String runId) {
        List<TraceEvent> out = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement("""
                SELECT run_id, worker_id, type, message, data_json, at
                FROM trace_event WHERE run_id = ? ORDER BY id ASC""")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TraceEvent(rs.getString("run_id"), rs.getString("worker_id"),
                            TraceEventType.valueOf(rs.getString("type")),
                            rs.getString("message"), rs.getString("data_json"), rs.getLong("at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询 trace 失败: " + runId, e);
        }
        return out;
    }

    /** 某次 run 的工具调用明细（按写入顺序）。 */
    public List<ToolEvent> toolEventsFor(String runId) {
        List<ToolEvent> out = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement("""
                SELECT run_id, worker_id, tool, args, success, result, elapsed_ms, at
                FROM tool_event WHERE run_id = ? ORDER BY id ASC""")) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ToolEvent(rs.getString("run_id"), rs.getString("worker_id"),
                            rs.getString("tool"), rs.getString("args"),
                            rs.getInt("success") == 1, rs.getString("result"),
                            rs.getLong("elapsed_ms"), rs.getLong("at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询 tool_event 失败: " + runId, e);
        }
        return out;
    }

    /** 某次 run 中出现过的 Worker 名字（去重、保持出现顺序）。 */
    public List<String> workersOf(String runId) {
        List<String> out = new ArrayList<>();
        for (TraceEvent e : eventsFor(runId)) {
            if (e.workerId() != null && !out.contains(e.workerId())) {
                out.add(e.workerId());
            }
        }
        return out;
    }

    /** 一次工具调用的可观测明细。 */
    public record ToolEvent(String runId, String workerId, String tool, String args,
                            boolean success, String result, long elapsedMs, long at) {
    }
}