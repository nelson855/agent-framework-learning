package com.example.agentlearning.stage04;

/**
 * 模型的一次决策（Model Decision）：调用某个工具，或给出最终回答。
 *
 * <p>携带可选的 {@code decisionSummary} —— 这一步为什么这么做的一句话解释。
 * 教学边界：不要求模型输出完整思维链，只接受这种简短结构化解释。
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