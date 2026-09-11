package com.example.agentlearning.stage03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContextBuilderTest {

    private Database db() {
        return new Database("jdbc:sqlite::memory:");
    }

    private AgentState state(boolean compacted) {
        return new AgentState("r1", "制定开发计划", List.of(
                PlanStep.pending("S1", "收集需求", "echo", "a"),
                PlanStep.pending("S2", "设计模型", "echo", "b")),
                List.of("S1: 完成收集"), compacted);
    }

    private KnowledgeDoc doc(String id, String title) {
        return new KnowledgeDoc(id, title, "主键 TEXT UUID，下划线命名。", "sqlite", Instant.now().toString());
    }

    @Test
    void contextIncludesRagDocsAndMemories() {
        try (Database db = db()) {
            ContextSnapshotRepository snapRepo = new ContextSnapshotRepository(db);
            CompactionSummaryRepository compRepo = new CompactionSummaryRepository(db);
            ContextBuilder builder = new ContextBuilder(snapRepo, compRepo);

            List<KnowledgeDoc> docs = List.of(doc("1", "数据模型规范"));
            List<Memory> mems = List.of(new Memory("m", "u", "preference", "用户偏好 SQLite",
                    8, Instant.now().toString(), Instant.now().toString()));

            String ctx = builder.build("r1", state(false), 0, docs, mems);

            assertTrue(ctx.contains("数据模型规范"));
            assertTrue(ctx.contains("用户偏好 SQLite"));
            assertTrue(ctx.contains("=== PLAN ==="));
            // 快照被记录
            assertFalse(snapRepo.listSnapshots("r1").isEmpty());
            assertEquals(1, snapRepo.listRagDocs("r1").size());
        }
    }

    @Test
    void compactedContextShowsSummaryInsteadOfRaw() {
        try (Database db = db()) {
            ContextSnapshotRepository snapRepo = new ContextSnapshotRepository(db);
            CompactionSummaryRepository compRepo = new CompactionSummaryRepository(db);
            compRepo.save("r1", new CompactionSummary(1, "制定开发计划",
                    List.of("收集完成"), List.of(), List.of(), List.of("生成总结")));
            ContextBuilder builder = new ContextBuilder(snapRepo, compRepo);

            String ctx = builder.build("r1", state(true), 1, List.of(), List.of());
            assertTrue(ctx.contains("COMPACTED SUMMARY"));
            assertFalse(ctx.contains("S1: 完成收集")); // 原始结果被摘要替代
        }
    }
}