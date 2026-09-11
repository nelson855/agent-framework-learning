package com.example.agentlearning.stage03;

import java.util.List;

/**
 * 上下文压缩器：当累计结果超过阈值，压缩为结构化摘要并保存。
 *
 * <p>压缩后返回新的 {@link AgentState}（compacted=true），原始 {@code stepResults}
 * 保留不动（不销毁历史），后续 context 构建会用摘要替换旧结果。
 */
public final class Compactor {

    private final ContextPolicy policy;
    private final CompactionSummarizer summarizer;
    private final CompactionSummaryRepository summaryRepo;

    public Compactor(ContextPolicy policy, CompactionSummarizer summarizer,
            CompactionSummaryRepository summaryRepo) {
        this.policy = policy;
        this.summarizer = summarizer;
        this.summaryRepo = summaryRepo;
    }

    /** 检查是否需要压缩；若需要则生成摘要并保存，返回 compacted state；否则返回原 state。 */
    public AgentState maybeCompact(AgentState state) {
        if (state.compacted()) {
            return state;
        }
        int totalChars = 0;
        for (String r : state.stepResults()) {
            totalChars += r != null ? r.length() : 0;
        }
        if (!policy.shouldCompact(totalChars)) {
            return state;
        }
        // 压缩
        int version = summaryRepo.listSummaries(state.runId()).size() + 1;
        CompactionSummary summary = summarizer.summarize(state.runId(), state.goal(), state.stepResults());
        summary = new CompactionSummary(version, summary.goal(), summary.completed(),
                summary.importantFacts(), summary.decisions(), summary.pendingActions());
        summaryRepo.save(state.runId(), summary);
        System.out.println("[COMPACT] stepResults " + state.stepResults().size()
                + " 条 → Summary v" + version);
        return new AgentState(state.runId(), state.goal(), state.plan(), state.stepResults(), true);
    }
}