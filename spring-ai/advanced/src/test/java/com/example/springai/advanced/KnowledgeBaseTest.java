package com.example.springai.advanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 知识库 ingest / retrieval 离线测试：不调真实 Embedding 模型，
 * 用 {@link KeywordEmbeddingModel} 保证行为可预测。
 */
class KnowledgeBaseTest {

    private PolicyKnowledgeBase knowledgeBase;

    @BeforeEach
    void setUp() {
        knowledgeBase = new PolicyKnowledgeBase(new KeywordEmbeddingModel());
    }

    @Test
    void ingestSplitsMarkdownIntoThreeSections() {
        assertEquals(3, knowledgeBase.sectionCount());
    }

    @Test
    void blockedQueryHitsBlockedSectionFirst() {
        List<PolicyKnowledgeBase.Segment> hits = knowledgeBase.search("BLOCKED 任务应该怎么处理", 2);

        assertEquals(2, hits.size());
        assertEquals("BLOCKED 任务升级处理", hits.get(0).title());
        assertTrue(hits.get(0).text().contains("4 小时内发起升级"),
                "命中片段应包含升级时限原文，可追踪来源");
    }

    @Test
    void blankQueryReturnsEmpty() {
        assertTrue(knowledgeBase.search("   ", 2).isEmpty());
        assertTrue(knowledgeBase.search(null, 2).isEmpty());
        assertTrue(knowledgeBase.search("BLOCKED", 0).isEmpty());
    }
}
