package com.example.agentlearning.stage02;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 共享的 SQLite 连接 + 全部表的自动建表。
 *
 * <p>本 Stage 把所有"需要持久化"的东西都放进数据库，但它们是<b>六张不同的表</b>，
 * 语义互不相同（见 {@code initSchema} 里的注释）：
 * conversation（会话外壳）、message（对话历史）、agent_run（Agent 运行状态）、
 * plan + plan_step（结构化计划与每步状态）、memory（长期记忆）、task（工具操作的数据）。
 *
 * <p>这正是一个核心教学点：<b>这六张表不能被捏成一张 messages 列表。</b>
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
                    CREATE TABLE IF NOT EXISTS conversation (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )"""); // 会话外壳
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS message (
                        id TEXT PRIMARY KEY,
                        conversation_id TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )"""); // 对话历史（Conversation History）
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS agent_run (
                        run_id TEXT PRIMARY KEY,
                        conversation_id TEXT NOT NULL,
                        goal TEXT NOT NULL,
                        status TEXT NOT NULL,
                        current_step INTEGER NOT NULL,
                        started_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )"""); // Agent 运行状态（Agent State）
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS plan (
                        id TEXT PRIMARY KEY,
                        run_id TEXT NOT NULL,
                        goal TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )"""); // 计划头（Plan）
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS plan_step (
                        id TEXT PRIMARY KEY,
                        plan_id TEXT NOT NULL,
                        step_no INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        status TEXT NOT NULL,
                        failure_reason TEXT
                    )"""); // 计划每步状态
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS memory (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        type TEXT NOT NULL,
                        content TEXT NOT NULL,
                        importance INTEGER NOT NULL,
                        created_at TEXT NOT NULL,
                        last_used_at TEXT
                    )"""); // 长期记忆（Long-term Memory）
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS task (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        status TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )"""); // 任务数据（工具操作的对象）
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

    /** 统计某张表的行数（测试断言用）。 */
    public int countRows(String table) {
        try (Statement st = connection().createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("统计表行数失败: " + table, e);
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