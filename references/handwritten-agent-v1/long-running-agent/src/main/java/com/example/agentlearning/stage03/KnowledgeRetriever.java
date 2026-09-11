package com.example.agentlearning.stage03;

import java.util.List;

/**
 * 知识检索器：用当前任务目标去 {@code knowledge_doc} 表里找相关规范文档。
 */
public final class KnowledgeRetriever {

    private final KnowledgeRepository repository;

    public KnowledgeRetriever(KnowledgeRepository repository) {
        this.repository = repository;
    }

    public List<KnowledgeDoc> retrieve(String query, int limit) {
        List<String> keywords = Tokens.tokenize(query);
        return repository.search(keywords, List.of(), limit);
    }
}