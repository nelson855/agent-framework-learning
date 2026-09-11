package com.example.agentlearning.stage02;

/**
 * 计划步骤的状态。
 *
 * <p>PENDING 待执行 / RUNNING 执行中 / DONE 完成 / FAILED 失败（触发 Replan）/
 * SKIPPED 跳过（Replan 上限到达后放弃剩余步骤）。
 */
public enum PlanStepStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    SKIPPED
}