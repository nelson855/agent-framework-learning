package com.example.agentlearning.stage04;

/**
 * 一次 Agent Run 的状态机。
 */
public enum RunStatus {
    /** 正在执行（或等待恢复）。 */
    RUNNING,
    /** 因高风险动作停在等待人批准。 */
    WAITING_APPROVAL,
    /** 正常给出 final 并完成评估。 */
    COMPLETED,
    /** 达到步数上限仍未收敛。 */
    MAX_STEP_EXCEEDED
}