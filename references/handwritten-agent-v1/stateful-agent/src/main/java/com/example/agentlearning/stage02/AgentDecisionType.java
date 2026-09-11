package com.example.agentlearning.stage02;

/**
 * 模型的一次决策：调用工具，或给出最终回答。
 */
public enum AgentDecisionType {
    TOOL_CALL,
    FINAL
}