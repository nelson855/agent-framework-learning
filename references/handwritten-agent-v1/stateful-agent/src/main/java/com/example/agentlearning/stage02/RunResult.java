package com.example.agentlearning.stage02;

/**
 * 一次 Agent 运行的产出：运行 id（可用于查看 State）、最终回答、落库后的运行状态。
 */
public record RunResult(String runId, String answer, AgentRun run) {
}