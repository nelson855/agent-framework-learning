package com.example.agentlearning.stage03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompactorTest {

    private Database db() {
        return new Database("jdbc:sqlite::memory:");
    }

    private AgentState state(boolean compacted, String... results) {
        return new AgentState("r1", "goal", List.of(), List.of(results), compacted);
    }

    @Test
    void compactsWhenThresholdReached() {
        try (Database db = db()) {
            CompactionSummaryRepository sumRepo = new CompactionSummaryRepository(db);
            Compactor compactor = new Compactor(new ContextPolicy(10),
                    new CompactionSummarizer(ScriptedLlmClient.of(
                            "{\"goal\":\"g\",\"completed\":[],\"importantFacts\":[],"
                                    + "\"decisions\":[],\"pendingActions\":[]}")),
                    sumRepo);

            AgentState small = state(false, "short");
            AgentState after = compactor.maybeCompact(small);
            // 8 字符 < 阈值 10，不压缩
            assertFalse(after.compacted());

            AgentState big = state(false, "这是一段长度远超阈值的步骤结果文本".repeat(3));
            AgentState compacted = compactor.maybeCompact(big);
            assertTrue(compacted.compacted());
            assertEquals(1, sumRepo.listSummaries("r1").size());
        }
    }

    @Test
    void doesNotCompactTwice() {
        try (Database db = db()) {
            CompactionSummaryRepository sumRepo = new CompactionSummaryRepository(db);
            Compactor compactor = new Compactor(new ContextPolicy(1),
                    new CompactionSummarizer(ScriptedLlmClient.of(
                            "{\"goal\":\"g\",\"completed\":[],\"importantFacts\":[],"
                                    + "\"decisions\":[],\"pendingActions\":[]}")),
                    sumRepo);

            AgentState already = state(true, "x");
            AgentState after = compactor.maybeCompact(already);
            assertTrue(after.compacted());
            // 已经压缩过，不再产生新的摘要
            assertEquals(0, sumRepo.listSummaries("r1").size());
        }
    }
}