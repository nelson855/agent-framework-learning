package com.example.agentlearning.stage03;

/**
 * 上下文策略：何时触发压缩。
 *
 * @param compactThreshold 累计 stepResults 字符数达到该值后触发压缩（含等于）
 */
public record ContextPolicy(int compactThreshold) {

    public static final int DEFAULT_THRESHOLD = 100;

    public ContextPolicy {
        if (compactThreshold <= 0) {
            compactThreshold = DEFAULT_THRESHOLD;
        }
    }

    public ContextPolicy() {
        this(DEFAULT_THRESHOLD);
    }

    public boolean shouldCompact(int totalChars) {
        return totalChars >= compactThreshold;
    }
}