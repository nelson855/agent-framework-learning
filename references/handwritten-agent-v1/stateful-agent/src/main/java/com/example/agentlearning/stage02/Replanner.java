package com.example.agentlearning.stage02;

import java.util.List;

/**
 * 再规划器：某一步失败后，基于"原目标 + 当前计划状态 + 失败步骤与原因"重新生成计划。
 *
 * <p>只在失败时被调用（由 {@link StatefulAgentRunner} 保证），避免"每一步都 Replan"导致成本失控。
 */
public final class Replanner {

    private static final String PROMPT = """
            你是任务再规划器。原计划中某一步执行失败了，请重新规划剩余步骤。
            规则：
            - 已完成步骤的 id 请保留不变；
            - 失败步骤可以补充前置条件、拆细或调整顺序，但 id 尽量沿用；
            - 只输出一个 JSON，不要输出任何其他文字：
            {"goal":"目标","steps":[{"id":"S1","description":"步骤描述"},...]}""";

    private final LlmClient llm;

    public Replanner(LlmClient llm) {
        this.llm = llm;
    }

    public Plan replan(Plan current, PlanStep failed) {
        LlmResponse reply = llm.chat(List.of(
                Message.system(PROMPT),
                Message.user(buildContext(current, failed))));
        return PlanParser.parse(reply.content());
    }

    private String buildContext(Plan current, PlanStep failed) {
        StringBuilder sb = new StringBuilder();
        sb.append("原目标: ").append(current.goal()).append('\n');
        sb.append("当前计划状态:\n");
        for (PlanStep step : current.steps()) {
            sb.append("- [").append(step.id()).append("] ").append(step.description())
                    .append(" (").append(step.status());
            if (step.failureReason() != null) {
                sb.append(", 失败原因: ").append(step.failureReason());
            }
            sb.append(")\n");
        }
        sb.append("失败步骤: [").append(failed.id()).append("] ").append(failed.description())
                .append(", 失败原因: ").append(failed.failureReason());
        return sb.toString();
    }
}