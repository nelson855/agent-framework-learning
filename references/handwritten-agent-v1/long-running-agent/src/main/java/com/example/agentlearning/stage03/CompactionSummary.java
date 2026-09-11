package com.example.agentlearning.stage03;

import java.util.List;

/**
 * 一次上下文压缩产生的结构化摘要（对应 {@code compaction_summary} 表）。
 *
 * <p>压缩不是"丢掉"，而是把早期步骤的结果提炼成可恢复的 JSON：
 * goal / completed / importantFacts / decisions / pendingActions。
 *
 * @param version         压缩版本
 * @param goal            任务目标
 * @param completed       已完成的事项
 * @param importantFacts  关键事实
 * @param decisions       做过的决定
 * @param pendingActions  尚未完成、需继续的动作
 */
public record CompactionSummary(
        int version,
        String goal,
        List<String> completed,
        List<String> importantFacts,
        List<String> decisions,
        List<String> pendingActions) {
}