package com.example.agentlearning.stage03;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * 第二层：LLM Evaluator —— 只判断语义，不再生成。
 *
 * <p>输入已通过 {@link ProgramValidator} 结构校验，这里用模型判断两类无法用代码编码的标准：
 * summary 是否覆盖了计划完成情况；recommendations 是否可执行。输出 JSON：
 * <pre>{"pass": true/false, "score": 0-4, "issues": []}</pre>
 * 输出无法解析时按「不通过」处理，避免放行坏输出。
 */
public final class LlmEvaluator {

    private static final String PROMPT = """
            你是一名交付质量评审（Evaluator）。只做语义判断，不重新生成总结。
            依据 Rubric 打 0~4 分：
            - 完整性：summary 是否概括了计划、关键决策与结果（2 分）；
            - 可执行性：recommendations 是否具体、可落地（2 分）。
            分数 >= 3 即通过。
            只输出 JSON：
            {"pass": true 或 false, "score": 整数, "issues": ["未达标的具体问题，可为空数组"]}
            """;

    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmEvaluator(LlmClient llm) {
        this.llm = llm;
    }

    public EvaluatorFeedback evaluate(FinalReport report) {
        String reply = llm.chat(List.of(
                Message.system(PROMPT),
                Message.user("待评审交付总结:\n" + report)))
                .content();
        try {
            JsonNode root = mapper.readTree(reply);
            boolean pass = root.path("pass").asBoolean(false);
            int score = root.path("score").asInt(0);
            List<String> issues = mapper.convertValue(root.path("issues"),
                    new TypeReference<List<String>>() {
                    });
            return new EvaluatorFeedback(pass, score, issues);
        } catch (Exception e) {
            return EvaluatorFeedback.failed(0, "评估输出无法解析为 JSON: " + e.getMessage());
        }
    }
}