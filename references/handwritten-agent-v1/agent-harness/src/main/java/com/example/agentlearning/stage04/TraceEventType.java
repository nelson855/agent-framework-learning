package com.example.agentlearning.stage04;

/**
 * 一次 Agent Run 内部的可观察事件类型（Harness 的 Trace 语言）。
 *
 * <p>只记录可公开的运行事件与结构化决策摘要，<b>不记录隐藏 chain-of-thought</b>。
 */
public enum TraceEventType {
    /** 一次对模型的调用。 */
    MODEL_CALL,
    /** Agent 决定调用一个工具。 */
    TOOL_CALL,
    /** 工具执行的结果。 */
    TOOL_RESULT,
    /** 状态/上下文发生变化。 */
    STATE_CHANGED,
    /** 检索到工作记忆。 */
    MEMORY_RETRIEVED,
    /** 为模型构建了一次上下文。 */
    CONTEXT_BUILT,
    /** 保存了一次 Checkpoint。 */
    CHECKPOINT_SAVED,
    /** 高风险工具需要人工批准。 */
    APPROVAL_REQUIRED,
    /** 人工对审批做出决定（批准/拒绝）。 */
    APPROVAL_RESOLVED,
    /** Orchestrator 把子任务交接给 Worker（Multi-Agent Handoff）。 */
    HANDOFF,
    /** Evaluator 对一次 run 的判定。 */
    EVALUATION,
    /** 一次 run 结束。 */
    RUN_FINISHED
}