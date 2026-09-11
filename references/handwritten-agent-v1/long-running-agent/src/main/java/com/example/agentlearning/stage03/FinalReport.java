package com.example.agentlearning.stage03;

import java.util.List;

/**
 * 最终交付总结：由全部步骤结果汇总生成，符合规范的 JSON。
 *
 * @param projectName      项目名
 * @param planSteps        计划总步数
 * @param completedSteps   已完成步骤编号
 * @param summary          总结正文
 * @param recommendations  后续建议
 */
public record FinalReport(
        String projectName,
        int planSteps,
        List<String> completedSteps,
        String summary,
        List<String> recommendations) {
}