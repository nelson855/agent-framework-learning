package com.example.agentlearning.stage03;

import java.util.List;

/**
 * 上下文构建器：组装计划状态、步骤结果（或压缩摘要）、RAG、记忆，
 * 生成给 LongRunner 的上下文文本，并记录 {@link ContextSnapshot}。
 *
 * <p>Context Inspector 区分四块：RAG retrieved docs、memory retrieved items、
 * selected context、compacted summary。
 */
public final class ContextBuilder {

    private final ContextSnapshotRepository snapshotRepo;
    private final CompactionSummaryRepository compactionRepo;

    public ContextBuilder(ContextSnapshotRepository snapshotRepo,
            CompactionSummaryRepository compactionRepo) {
        this.snapshotRepo = snapshotRepo;
        this.compactionRepo = compactionRepo;
    }

    /**
     * 构建当前步骤的上下文文本并记录快照。
     *
     * @return 用于模型调用的上下文文本（本阶段不真正调模型，仅观察）
     */
    public String build(String runId, AgentState state, int stepIndex,
            List<KnowledgeDoc> ragDocs, List<Memory> memories) {
        StringBuilder sb = new StringBuilder();

        // 1) Plan status
        sb.append("=== PLAN ===\n");
        for (int i = 0; i < state.plan().size(); i++) {
            PlanStep step = state.plan().get(i);
            String marker = switch (step.status()) {
                case DONE -> "[DONE]";
                case RUNNING -> "[RUNNING]";
                case PENDING -> "[PENDING]";
            };
            sb.append("  ").append(marker).append(" ").append(step.id())
                    .append(": ").append(step.description()).append('\n');
        }

        // 2) Step results 或 compressed summary
        sb.append("\n=== STEP RESULTS ===\n");
        String compactedText = compactionRepo.latestSummaryText(runId);
        if (state.compacted() && compactedText != null) {
            sb.append("[COMPACTED SUMMARY]\n").append(compactedText);
            sb.append("\n(Resume 后继续)\n");
        } else if (!state.stepResults().isEmpty()) {
            for (String r : state.stepResults()) {
                sb.append("  - ").append(r).append('\n');
            }
        } else {
            sb.append("  (暂无)\n");
        }

        // 3) Retrieved memory
        sb.append("\n=== RETRIEVED MEMORY ===\n");
        if (memories.isEmpty()) {
            sb.append("  (无命中)\n");
        } else {
            for (Memory m : memories) {
                sb.append("  - [").append(m.type()).append("] ").append(m.content()).append('\n');
            }
        }

        // 4) Retrieved knowledge (RAG)
        sb.append("\n=== RETRIEVED KNOWLEDGE (RAG) ===\n");
        if (ragDocs.isEmpty()) {
            sb.append("  (无命中)\n");
        } else {
            for (KnowledgeDoc d : ragDocs) {
                sb.append("  - [").append(d.title()).append("] ")
                        .append(snippet(d.content())).append('\n');
            }
        }

        String contextText = sb.toString();

        // 记录 snapshot
        String snapshotId = "snap-" + runId + "-" + stepIndex;
        snapshotRepo.save(new ContextSnapshot(snapshotId, runId, stepIndex,
                ragDocs, memories, contextText,
                state.compacted() ? compactedText : null,
                java.time.Instant.now().toString()));

        return contextText;
    }

    private static String snippet(String content) {
        if (content == null) {
            return "";
        }
        String oneLine = content.replace('\n', ' ').strip();
        return oneLine.length() > 300 ? oneLine.substring(0, 300) + "…" : oneLine;
    }
}