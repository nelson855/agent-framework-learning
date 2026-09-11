package com.example.agentlearning.stage03;

import java.util.List;

/**
 * 一次"构建上下文"的结果快照，对应 {@code context_snapshot} 表。
 * 供 Web Context Inspector 区分展示四块内容，而不是一个大字符串。
 *
 * @param id              快照标识
 * @param runId           所属运行
 * @param stepIndex       是"准备第几步"时构建的（0 基）
 * @param ragDocs         RAG 检索到的知识文档
 * @param memories        检索到的用户长期记忆
 * @param selectedContext 选进上下文的文本（含最新结果）
 * @param compactedSummary 若有压缩，其摘要文本（否则 null）
 * @param createdAt       构建时刻
 */
public record ContextSnapshot(
        String id,
        String runId,
        int stepIndex,
        List<KnowledgeDoc> ragDocs,
        List<Memory> memories,
        String selectedContext,
        String compactedSummary,
        String createdAt) {
}