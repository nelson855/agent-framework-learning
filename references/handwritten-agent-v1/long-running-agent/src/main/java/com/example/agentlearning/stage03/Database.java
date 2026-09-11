package com.example.agentlearning.stage03;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 连接 + 自动建表。
 *
 * <p>本模块涉及的 7 张表：
 * <ul>
 *   <li>{@code run} — 运行实例</li>
 *   <li>{@code agent_checkpoint} — 检查点（版本化）</li>
 *   <li>{@code context_snapshot} — 每次构建上下文的快照</li>
 *   <li>{@code compaction_summary} — 压缩后的结构化摘要</li>
 *   <li>{@code evaluation} — 评估与校验结果</li>
 *   <li>{@code knowledge_doc} — RAG 知识文档</li>
 *   <li>{@code memory} — 用户长期记忆</li>
 * </ul>
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
        try (Statement st = connection().createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS run (
                        id TEXT PRIMARY KEY,
                        goal TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        current_step INTEGER NOT NULL DEFAULT 0,
                        started_at TEXT NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS agent_checkpoint (
                        id TEXT PRIMARY KEY,
                        run_id TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        state_json TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (run_id) REFERENCES run(id)
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS context_snapshot (
                        id TEXT PRIMARY KEY,
                        run_id TEXT NOT NULL,
                        step_index INTEGER NOT NULL,
                        rag_docs_json TEXT,
                        memories_json TEXT,
                        selected_context TEXT,
                        compacted_summary TEXT,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (run_id) REFERENCES run(id)
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS compaction_summary (
                        id TEXT PRIMARY KEY,
                        run_id TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        summary_json TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (run_id) REFERENCES run(id)
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS evaluation (
                        id TEXT PRIMARY KEY,
                        run_id TEXT NOT NULL,
                        iteration INTEGER NOT NULL,
                        validator_pass INTEGER NOT NULL,
                        validator_errors TEXT,
                        evaluator_pass INTEGER,
                        evaluator_score INTEGER,
                        evaluator_issues TEXT,
                        report_text TEXT,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (run_id) REFERENCES run(id)
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS knowledge_doc (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        created_at TEXT NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS memory (
                        id TEXT PRIMARY KEY,
                        user_id TEXT NOT NULL,
                        type TEXT NOT NULL,
                        content TEXT NOT NULL,
                        importance INTEGER NOT NULL DEFAULT 5,
                        created_at TEXT NOT NULL,
                        last_used_at TEXT
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
            }
        }
    }
}