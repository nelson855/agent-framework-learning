package com.example.agentlearning.stage03;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 第一层：确定性校验（Program Validator）。
 *
 * <p>凡能由程序确定的判断（JSON 可解析、必填字段、类型、非空集合）都在此完成，
 * <b>不调用模型</b>。只有结构合法才交给 LLM Evaluator 做语义判断。
 */
public final class ProgramValidator {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 校验结果：通过与否 + 错误列表；合法时附带解析出的 {@link FinalReport}。 */
    public record ValidationResult(boolean valid, List<String> errors, FinalReport report) {

        static ValidationResult rejected(List<String> errors) {
            return new ValidationResult(false, errors, null);
        }
    }

    public ValidationResult validate(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return ValidationResult.rejected(List.of("JSON 为空"));
        }
        JsonNode root;
        try {
            root = mapper.readTree(stripCodeFence(rawText));
        } catch (Exception e) {
            return ValidationResult.rejected(List.of("JSON 无法解析: " + e.getMessage()));
        }

        List<String> errors = new ArrayList<>();
        requireString(root, "projectName", errors);
        if (!root.has("planSteps") || !root.get("planSteps").isNumber()) {
            errors.add("数字字段缺失或类型错误: planSteps");
        }
        requireNonEmptyArray(root, "completedSteps", errors);
        requireString(root, "summary", errors);
        requireNonEmptyArray(root, "recommendations", errors);

        if (!errors.isEmpty()) {
            return ValidationResult.rejected(errors);
        }
        try {
            return new ValidationResult(true, List.of(), mapper.treeToValue(root, FinalReport.class));
        } catch (Exception e) {
            return ValidationResult.rejected(List.of("无法反序列化为 FinalReport: " + e.getMessage()));
        }
    }

    private static String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl >= 0 && lastFence > firstNl) {
                return trimmed.substring(firstNl + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static void requireString(JsonNode root, String field, List<String> errors) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            errors.add("必填字段缺失或为空: " + field);
        }
    }

    private static void requireNonEmptyArray(JsonNode root, String field, List<String> errors) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray() || node.isEmpty()) {
            errors.add("数组字段缺失或为空: " + field);
        }
    }
}