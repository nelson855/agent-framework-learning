package com.example.agentlearning.stage03;

import java.util.List;

/**
 * 最终报告生成器：用 LLM 把步骤结果汇总为一份 JSON 交付总结。
 * 返回原始文本，交 {@link ProgramValidator} 做确定性校验。
 */
public final class FinalReportGenerator {

    private static final String PROMPT = """
            你是一个 Long-running Agent 的最终交付生成器。
            根据给定的「任务目标」和「已执行步骤结果」，生成一份符合规范的 JSON 交付总结。

            只输出 JSON，不要多余文字：
            {
              "projectName": "项目名",
              "planSteps": 6,
              "completedSteps": ["S1", "S2", ...],
              "summary": "整体总结，说明完成了什么、关键决策",
              "recommendations": ["后续建议"]
            }
            每条精简。
            """;

    private final LlmClient llm;

    public FinalReportGenerator(LlmClient llm) {
        this.llm = llm;
    }

    /** 生成 JSON 总结（可能不合法，交给 Validator 决定）。 */
    public String generate(String goal, List<String> stepResults) {
        StringBuilder context = new StringBuilder("任务目标: ").append(goal).append("\n\n已执行步骤结果:\n");
        for (String r : stepResults) {
            context.append("- ").append(r).append('\n');
        }
        return llm.chat(List.of(Message.system(PROMPT), Message.user(context.toString()))).content();
    }
}