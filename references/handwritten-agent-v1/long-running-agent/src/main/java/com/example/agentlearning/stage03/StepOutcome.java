package com.example.agentlearning.stage03;

/**
 * 单步执行的结果，供 RunService / Web 观察。
 *
 * @param status      执行后运行状态（RUNNING=继续推进 / INTERRUPTED=被中断 / COMPLETED=已全部完成）
 * @param stepId      本次执行到的步骤 id（若无则空）
 * @param stepIndex   本次执行到的步骤下标（0 基，-1 表示无）
 * @param message     人类可读说明
 */
public record StepOutcome(RunStatus status, String stepId, int stepIndex, String message) {
}