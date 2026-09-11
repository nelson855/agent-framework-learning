package com.example.agentlearning.stage04;

/**
 * 模型一次决策的类型：
 * <ul>
 *   <li>{@link #TOOL_CALL}：要调用某个工具（Action）</li>
 *   <li>{@link #FINAL}：已有足够信息，给出最终回答（终止 Loop）</li>
 * </ul>
 */
public enum AgentDecisionType {
    TOOL_CALL,
    FINAL
}