package com.example.agentlearning.stage02;

/**
 * 模型的一次决策：调用工具，或给出最终回答。
 */
public record AgentDecision(
        AgentDecisionType type,
        ToolCall toolCall,
        String answer,
        String decisionSummary) {

    public static AgentDecision toolCall(ToolCall call, String decisionSummary) {
        return new AgentDecision(AgentDecisionType.TOOL_CALL, call, null, decisionSummary);
    }

    public static AgentDecision finalAnswer(String answer) {
        return new AgentDecision(AgentDecisionType.FINAL, null, answer, null);
    }

    public boolean isFinal() {
        return type == AgentDecisionType.FINAL;
    }
}