package com.example.agentlearning.stage03;

/**
 * 一次运行的一条检查点，对应 {@code agent_checkpoint} 表的一行。
 *
 * @param id          检查点标识
 * @param runId       所属运行
 * @param version     版本号（每次保存 +1，不覆盖历史）
 * @param savedAt     保存时刻
 * @param currentStep 保存时的步骤下标
 * @param state       还原出的可恢复状态
 */
public record VersionedCheckpoint(
        String id,
        String runId,
        int version,
        String savedAt,
        int currentStep,
        AgentState state) {
}