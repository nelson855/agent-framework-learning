package com.example.agentlearning.stage03;

import java.util.List;

/**
 * 评估器的反馈。
 *
 * @param pass   是否通过
 * @param score  评分（越高越好）
 * @param issues 未达标的具体问题
 */
public record EvaluatorFeedback(boolean pass, int score, List<String> issues) {

    public static EvaluatorFeedback accepted(int score) {
        return new EvaluatorFeedback(true, score, List.of());
    }

    /** 当评估输出本身无法解析时，按「不通过」处理并给出确定性原因。 */
    public static EvaluatorFeedback failed(int score, String reason) {
        return new EvaluatorFeedback(false, score, List.of(reason));
    }
}