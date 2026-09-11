package com.example.agentlearning.stage03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheckpointRepositoryTest {

    private Database db() {
        return new Database("jdbc:sqlite::memory:");
    }

    @Test
    void saveIncrementsVersionAndLoadsLatest() {
        try (Database db = db()) {
            CheckpointRepository repo = new CheckpointRepository(db);
            AgentState s1 = new AgentState("r1", "g", List.of(), List.of(), false);
            AgentState s2 = new AgentState("r1", "g", List.of(), List.of("step done"), true);

            assertEquals(1, repo.save(s1));
            assertEquals(2, repo.save(s2));
            assertEquals(2, repo.latestVersion("r1"));

            AgentState loaded = repo.loadLatest("r1").orElseThrow();
            assertTrue(loaded.compacted());
            assertEquals(1, loaded.stepResults().size());
        }
    }

    @Test
    void timelinePreservesOrderAndVersion() {
        try (Database db = db()) {
            CheckpointRepository repo = new CheckpointRepository(db);
            for (int i = 0; i < 3; i++) {
                repo.save(new AgentState("r1", "g", List.of(), List.of("r" + i), false));
            }
            List<VersionedCheckpoint> timeline = repo.listTimeline("r1");
            assertEquals(3, timeline.size());
            assertEquals(1, timeline.get(0).version());
            assertEquals(3, timeline.get(2).version());
            assertNotEquals(timeline.get(0).id(), timeline.get(1).id());
        }
    }

    @Test
    void emptyRunReturnsEmpty() {
        try (Database db = db()) {
            CheckpointRepository repo = new CheckpointRepository(db);
            assertTrue(repo.loadLatest("nope").isEmpty());
            assertEquals(0, repo.latestVersion("nope"));
        }
    }
}