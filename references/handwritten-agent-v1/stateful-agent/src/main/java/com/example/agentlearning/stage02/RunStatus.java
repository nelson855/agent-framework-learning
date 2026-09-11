package com.example.agentlearning.stage02;

/**
 * Agent 运行状态机。每次模型决策或工具执行都让状态发生一次转移，例如：
 *
 * <pre>
 * RUNNING → WAITING_TOOL → RUNNING → ... → COMPLETED
 *                                        ↘ FAILED
 * </pre>
 */
public enum RunStatus {
    RUNNING,
    WAITING_TOOL,
    COMPLETED,
    FAILED
}