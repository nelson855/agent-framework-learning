package com.example.langchain4j.stateful.domain;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 运行状态持久化，SQLite 直连。纯业务代码，不感知 AI。 */
public class AgentRunRepository implements AutoCloseable {

    private final Connection connection;

    public AgentRunRepository(String jdbcUrl) {
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS agent_run (
                            run_id TEXT PRIMARY KEY,
                            conversation_id TEXT NOT NULL,
                            goal TEXT NOT NULL,
                            status TEXT NOT NULL,
                            current_step INTEGER NOT NULL,
                            created_at INTEGER NOT NULL
                        )
                        """);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("open agent_run database failed: " + jdbcUrl, e);
        }
    }

    /** 内存库，测试用。 */
    public static AgentRunRepository inMemory() {
        return new AgentRunRepository("jdbc:sqlite::memory:");
    }

    public synchronized AgentRun createRun(String conversationId, String goal) {
        String runId = UUID.randomUUID().toString();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO agent_run (run_id, conversation_id, goal, status, current_step, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, runId);
            statement.setString(2, conversationId);
            statement.setString(3, goal);
            statement.setString(4, RunStatus.RUNNING.name());
            statement.setInt(5, 0);
            statement.setLong(6, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("create run failed", e);
        }
        return new AgentRun(runId, conversationId, goal, RunStatus.RUNNING, 0);
    }

    public synchronized Optional<AgentRun> findById(String runId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT run_id, conversation_id, goal, status, current_step FROM agent_run WHERE run_id = ?")) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AgentRun(
                        rows.getString("run_id"),
                        rows.getString("conversation_id"),
                        rows.getString("goal"),
                        RunStatus.valueOf(rows.getString("status")),
                        rows.getInt("current_step")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("find run failed: " + runId, e);
        }
    }

    public synchronized void updateStatus(String runId, RunStatus status, int currentStep) {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE agent_run SET status = ?, current_step = ? WHERE run_id = ?")) {
            statement.setString(1, status.name());
            statement.setInt(2, currentStep);
            statement.setString(3, runId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("run not found: " + runId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("update run failed: " + runId, e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("close agent_run database failed", e);
        }
    }
}
