package com.example.agentlearning.stage04;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 共享的 SQLite 连接 + 自动建表（Harness 的五张核心状态表）。
 *
 * <pre>
 * run             一次 Agent 运行（goal / status / current_step / plan_json）
 * trace_event     可观察事件时间线（Model / Tool / State / Approval / Handoff / Eval / Finish）
 * tool_event      工具调用明细（name / args / result / elapsed，供 Tool Events 面板）
 * checkpoint      每次状态落点（context_json 支持批准后/中断后恢复）
 * approval_request 高风险动作的人工审批请求
 * </pre>
 *
 * <p>Memory / Knowledge 设计为内存态（本模块不持久化文档内容），
 * 它们的“检索命中”通过 trace_event 的 MEMORY_RETRIEVED 观察。
 */
public final class Database implements AutoCloseable {

    private final String jdbcUrl;
    private Connection connection;

    public Database(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("缺少 SQLite jdbcUrl");
        }
        this.jdbcUrl = jdbcUrl;
        initSchema();
    }

    public void initSchema() {
        try (Statement statement = connection().createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS run (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        run_id       TEXT NOT NULL,
                        goal         TEXT NOT NULL,
                        status       TEXT NOT NULL,
                        current_step INTEGER NOT NULL DEFAULT 0,
                        plan_json    TEXT,
                        started_at   INTEGER NOT NULL,
                        ended_at     INTEGER
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS trace_event (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        run_id     TEXT NOT NULL,
                        worker_id  TEXT,
                        type       TEXT NOT NULL,
                        message    TEXT NOT NULL,
                        data_json  TEXT,
                        at         INTEGER NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS tool_event (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        run_id     TEXT NOT NULL,
                        worker_id  TEXT,
                        tool       TEXT NOT NULL,
                        args       TEXT NOT NULL,
                        success    INTEGER NOT NULL,
                        result     TEXT NOT NULL,
                        elapsed_ms INTEGER NOT NULL,
                        at         INTEGER NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS checkpoint (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        run_id       TEXT NOT NULL,
                        step_index   INTEGER NOT NULL,
                        context_json TEXT NOT NULL,
                        saved_at     INTEGER NOT NULL
                    )""");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS approval_request (
                        id             INTEGER PRIMARY KEY AUTOINCREMENT,
                        run_id         TEXT NOT NULL,
                        tool_call_json TEXT NOT NULL,
                        reason         TEXT NOT NULL,
                        risk           TEXT NOT NULL,
                        status         TEXT NOT NULL,
                        created_at     INTEGER NOT NULL,
                        decided_at     INTEGER
                    )""");
        } catch (SQLException e) {
            throw new IllegalStateException("初始化数据库失败: " + jdbcUrl, e);
        }
    }

    public Connection connection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(jdbcUrl);
            }
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("打开 SQLite 连接失败: " + jdbcUrl, e);
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // 关闭失败无需处理
            }
        }
    }
}