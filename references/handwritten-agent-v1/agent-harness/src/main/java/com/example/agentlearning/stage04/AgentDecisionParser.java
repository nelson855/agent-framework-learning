package com.example.agentlearning.stage04;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;

/**
 * 把模型输出的结构化 JSON 解析成 {@link AgentDecision}。
 *
 * <p>与模型约定的输出格式（写在 system prompt 里）：
 * <ul>
 *   <li>工具调用：{@code {"type":"tool_call","tool":"<名>","arguments":{...},"decisionSummary":"..."}}</li>
 *   <li>最终回答：{@code {"type":"final","answer":"..."}}</li>
 * </ul>
 *
 * <p>解析失败时按最终回答兜底（原样返回），不让解析异常打断 Loop。
 */
public final class AgentDecisionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentDecisionParser() {
    }

    public static AgentDecision parse(String modelReply) {
        try {
            JsonNode root = MAPPER.readTree(modelReply);
            String type = root.path("type").asText("");
            if ("tool_call".equals(type)) {
                String toolName = root.path("tool").asText("");
                Map<String, Object> arguments = toArguments(root.get("arguments"));
                String summary = root.path("decisionSummary").asText("");
                return AgentDecision.toolCall(new ToolCall(toolName, arguments), summary);
            }
            if ("final".equals(type)) {
                return AgentDecision.finalAnswer(root.path("answer").asText(modelReply));
            }
            return AgentDecision.finalAnswer(modelReply);
        } catch (IOException | RuntimeException e) {
            return AgentDecision.finalAnswer(modelReply);
        }
    }

    private static Map<String, Object> toArguments(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return MAPPER.convertValue(node, new TypeReference<Map<String, Object>>() {
        });
    }
}