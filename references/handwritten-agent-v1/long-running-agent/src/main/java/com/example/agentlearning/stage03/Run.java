package com.example.agentlearning.stage03;

import java.util.List;

/**
 * 一次运行的元信息。
 *
 * @param runId       运行标识
 * @param goal        本次任务目标
 * @param status      运行状态
 * @param currentStep 当前执行到的步骤下标（0 基）
 */
public record Run(String runId, String goal, RunStatus status, int currentStep) {
}