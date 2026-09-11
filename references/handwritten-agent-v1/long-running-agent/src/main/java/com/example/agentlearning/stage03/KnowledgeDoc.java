package com.example.agentlearning.stage03;

/**
 * 一篇知识文档，对应 {@code knowledge_doc} 表。
 * 语义：关于项目/外部世界，由本地规范文档导入。RAG 检索的对象。
 */
public record KnowledgeDoc(String id, String title, String content, String tags, String createdAt) {
}