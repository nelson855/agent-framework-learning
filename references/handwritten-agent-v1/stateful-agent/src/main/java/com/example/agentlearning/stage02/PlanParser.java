package com.example.agentlearning.stage02;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 把模型输出的计划 JSON 解析成 {@link Plan}（确定性校验，不交给模型"自己声称"）。
 *
 * <p>约定格式：
 * <pre>
 * {"goal":"目标","steps":[{"id":"S1","description":"..."},...]}
 * </pre>
 */
public final class PlanParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PlanParser() {
    }

    public static Plan parse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String goal = root.path("goal").asText("未命名目标");
            JsonNode stepsNode = root.path("steps");
            if (!stepsNode.isArray()) {
                throw new IllegalArgumentException("计划缺少 steps 数组: " + json);
            }
            List<PlanStep> steps = new ArrayList<>();
            for (JsonNode node : stepsNode) {
                String id = node.path("id").asText("");
                String description = node.path("description").asText("");
                if (id.isBlank() || description.isBlank()) {
                    throw new IllegalArgumentException("步骤缺少 id 或 description: " + node);
                }
                steps.add(new PlanStep(id, description));
            }
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("计划步骤不能为空: " + json);
            }
            return new Plan(goal, steps);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析计划 JSON: " + json, e);
        }
    }
}