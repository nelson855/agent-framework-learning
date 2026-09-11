package com.example.agentlearning.stage03;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 步骤结果摘要器：用 LLM 把一批步骤结果压缩成结构化摘要。
 *
 * <p>输出 JSON:
 * <pre>
 * {"goal":"...", "completed":[], "importantFacts":[], "decisions":[], "pendingActions":[]}
 * </pre>
 */
public final class CompactionSummarizer {

    private static final String PROMPT = """
            你是一个 Long-running Agent 的执行摘要压缩器。
            把给定的「已执行步骤结果」压缩成结构化 JSON，供后续步骤继续时使用上下文。

            只输出 JSON，不要多余文字：
            {
              "goal": "本任务的目标，一句话",
              "completed": ["已完成的事项"],
              "importantFacts": ["关键事实、技术决策"],
              "decisions": ["做过的决定"],
              "pendingActions": ["未完成、需要继续的动作"]
            }
            每条尽量精简。没有内容用空数组。
            """;

    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();

    public CompactionSummarizer(LlmClient llm) {
        this.llm = llm;
    }

    public CompactionSummary summarize(String runId, String goal, List<String> stepResults) {
        String transcript = "任务目标: " + goal + "\n\n已执行步骤的结果：\n";
        for (String r : stepResults) {
            transcript += "- " + r + "\n";
        }
        String reply = llm.chat(List.of(Message.system(PROMPT), Message.user(transcript))).content();
        try {
            JsonNode root = mapper.readTree(reply);
            // Support both array and string forms for flexible parsing
            List<String> completed = asStringList(root, "completed");
            List<String> facts = asStringList(root, "importantFacts");
            List<String> decisions = asStringList(root, "decisions");
            List<String> pending = asStringList(root, "pendingActions");
            return new CompactionSummary(1, // version assigned by caller
                    jsonString(root, "goal"),
                    completed, facts, decisions, pending);
        } catch (Exception e) {
            // fallback
            return new CompactionSummary(1, goal,
                    List.copyOf(stepResults), List.of("压缩解析失败: " + e.getMessage()),
                    List.of(), List.of("继续下一个步骤"));
        }
    }

    private List<String> asStringList(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null) {
            return List.of();
        }
        if (node.isArray()) {
            try {
                return mapper.convertValue(node, new TypeReference<List<String>>() {
                });
            } catch (Exception e) {
                return List.of(node.toString());
            }
        }
        if (node.isTextual()) {
            return List.of(node.asText());
        }
        return List.of(node.toString());
    }

    private String jsonString(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node != null ? node.asText("") : "";
    }
}