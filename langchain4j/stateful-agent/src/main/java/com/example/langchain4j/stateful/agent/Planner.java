package com.example.langchain4j.stateful.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import java.util.ArrayList;
import java.util.List;

/**
 * 拆计划：把用户目标拆成步骤清单。拆分靠模型，落库不管。
 *
 * <p>@FW-CMP [SCHEMA] 结构化输出靠提示词加手工解析
 *   手写（手写版 PlanParser）：
 *     // 模型输出纯文本，自己用 Jackson 抠 JSON，格式一歪就崩
 *   框架（下方 chat + Jackson）：
 *     // 直接调底层 ChatModel.chat(提示词)，再用 Jackson 读 {"steps":[...]}
 *   差异：本模块刻意没用 AiServices 的结构化输出，而是和手写版一样手工解析——
 *   为的是让同一模块同时展示"声明式"（TaskAgentService）和"底层直调"（本类）两种用法。
 *   Spring AI 版用了 BeanOutputConverter（格式约束由框架生成），
 *   本模块的格式约束是提示词里一句话，出错时自己兜底。
 *   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#6-schema-plan
 */
public class Planner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModel chatModel;

    public Planner(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** 把目标拆成步骤描述清单，不落库。 */
    public List<String> draftSteps(String goal) {
        String content = chatModel.chat(
                "把用户的目标拆成具体的执行步骤，只输出 JSON，如 {\"steps\":[\"第一步\",\"第二步\"]}。"
                        + "不要输出其他文字。\n目标：" + goal);
        return parseSteps(content);
    }

    static List<String> parseSteps(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String json = extractJson(content);
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode steps = root.isArray() ? root : root.get("steps");
            if (steps == null || !steps.isArray()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (JsonNode step : steps) {
                if (step.isTextual() && !step.asText().isBlank()) {
                    result.add(step.asText());
                }
            }
            return result;
        } catch (Exception e) {
            try {
                List<String> fallback = MAPPER.readValue(json, new TypeReference<>() {
                });
                return fallback.stream().filter(s -> s != null && !s.isBlank()).toList();
            } catch (Exception ignored) {
                return List.of();
            }
        }
    }

    private static String extractJson(String content) {
        String trimmed = content.strip();
        int start = Math.min(
                trimmed.indexOf('{') < 0 ? Integer.MAX_VALUE : trimmed.indexOf('{'),
                trimmed.indexOf('[') < 0 ? Integer.MAX_VALUE : trimmed.indexOf('['));
        int end = Math.max(trimmed.lastIndexOf('}'), trimmed.lastIndexOf(']'));
        if (start != Integer.MAX_VALUE && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}
