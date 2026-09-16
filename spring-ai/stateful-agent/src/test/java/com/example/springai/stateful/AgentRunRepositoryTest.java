package com.example.springai.stateful;

import com.example.springai.stateful.domain.AgentRun;
import com.example.springai.stateful.domain.AgentRunRepository;
import com.example.springai.stateful.domain.RunStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AgentRunRepository：一次运行的状态能创建、查回、推进。 */
class AgentRunRepositoryTest {

    @Test
    void createsAndFindsRun() {
        try (AgentRunRepository repository = AgentRunRepository.inMemory()) {
            AgentRun saved = repository.createRun("conv-1", "整理本周任务");

            var found = repository.findById(saved.runId());

            assertTrue(found.isPresent());
            assertEquals(RunStatus.RUNNING, found.get().status());
            assertEquals(0, found.get().currentStep());
        }
    }

    @Test
    void advancesRunStatus() {
        try (AgentRunRepository repository = AgentRunRepository.inMemory()) {
            AgentRun saved = repository.createRun("conv-1", "整理本周任务");

            repository.updateStatus(saved.runId(), RunStatus.DONE, 3);

            var found = repository.findById(saved.runId());
            assertTrue(found.isPresent());
            assertEquals(RunStatus.DONE, found.get().status());
            assertEquals(3, found.get().currentStep());
        }
    }
}
