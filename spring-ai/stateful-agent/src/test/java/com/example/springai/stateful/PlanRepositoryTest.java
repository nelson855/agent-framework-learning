package com.example.springai.stateful;

import com.example.springai.stateful.domain.Plan;
import com.example.springai.stateful.domain.PlanRepository;
import com.example.springai.stateful.domain.PlanStepStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PlanRepository：计划带步骤整体存取，步骤状态可推进。 */
class PlanRepositoryTest {

    @Test
    void savesAndLoadsPlanWithStepsInOrder() {
        try (PlanRepository repository = PlanRepository.inMemory()) {
            Plan saved = repository.createPlan("run-1", List.of("查任务", "建任务", "汇报"));

            var found = repository.findById(saved.id());

            assertTrue(found.isPresent());
            assertEquals(3, found.get().steps().size());
            assertEquals("查任务", found.get().steps().get(0).description());
            assertEquals(PlanStepStatus.PENDING, found.get().steps().get(0).status());
        }
    }

    @Test
    void updatesStepStatus() {
        try (PlanRepository repository = PlanRepository.inMemory()) {
            Plan saved = repository.createPlan("run-1", List.of("查任务"));
            String stepId = saved.steps().get(0).id();

            repository.updateStepStatus(saved.id(), stepId, PlanStepStatus.DONE, null);

            var found = repository.findById(saved.id());
            assertTrue(found.isPresent());
            assertEquals(PlanStepStatus.DONE, found.get().steps().get(0).status());
        }
    }
}
