package com.example.agentlearning.stage04;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Planner：为目标生成一份确定性步骤计划（教学上不调用 LLM）。
 *
 * <p>计划是给 Learner 与 Agent 展示的「分步意图」，Agent 借助它推进当前步骤；
 * 具体工具调用仍由 Agent 在 ReAct 循环里决定（不把决策硬编码成 if 分支）。
 */
public final class Planner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<PlanStep> plan(String goal) {
        List<PlanStep> steps = new ArrayList<>();
        steps.add(PlanStep.pending("S1", "读取本地知识文档（高风险操作规范）"));
        steps.add(PlanStep.pending("S2", "把关键规范写入工作记忆"));
        steps.add(PlanStep.pending("S3", "创建两个子任务"));
        steps.add(PlanStep.pending("S4", "把「核对删除策略」交接给 review-worker"));
        steps.add(PlanStep.pending("S5", "删除一个高风险任务（需人工批准）并总结评估"));
        return List.copyOf(steps);
    }

    public String toJson(List<PlanStep> steps) {
        try {
            return MAPPER.writeValueAsString(steps);
        } catch (IOException e) {
            throw new IllegalStateException("序列化 plan 失败", e);
        }
    }

    public List<PlanStep> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (IOException e) {
            return List.of();
        }
    }
}