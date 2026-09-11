package com.example.agentlearning.stage02;

import java.util.List;

/**
 * 任务规划器：调用模型把目标拆成结构化 {@link Plan}。
 *
 * <p>Planner 与 Agent 共用同一个模型，只是 Prompt 角色不同——这里是"先想清楚再动手"。
 */
public final class Planner {

    private static final String PROMPT = """
            你是任务规划器。把用户给出的目标分解为若干个可执行的学习步骤。
            只输出一个 JSON，不要输出任何其他文字：
            {"goal":"目标","steps":[{"id":"S1","description":"步骤描述"},{"id":"S2","description":"步骤描述"},...]}
            步骤 id 必须用 S1、S2、S3 依次编号，且至少 3 步。""";

    private final LlmClient llm;

    public Planner(LlmClient llm) {
        this.llm = llm;
    }

    public Plan createPlan(String goal) {
        LlmResponse reply = llm.chat(List.of(Message.system(PROMPT), Message.user(goal)));
        return PlanParser.parse(reply.content());
    }
}