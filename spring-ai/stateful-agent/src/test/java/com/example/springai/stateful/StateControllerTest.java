package com.example.springai.stateful;

import com.example.springai.stateful.domain.AgentRun;
import com.example.springai.stateful.domain.AgentRunRepository;
import com.example.springai.stateful.domain.PlanRepository;
import com.example.springai.stateful.web.StateController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 状态接口：凭运行编号查到运行状态和计划步骤。 */
class StateControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsRunWithPlanSteps() throws Exception {
        try (AgentRunRepository runs = AgentRunRepository.inMemory();
             PlanRepository plans = PlanRepository.inMemory()) {
            AgentRun run = runs.createRun("conv-1", "整理本周任务");
            plans.createPlan(run.runId(), List.of("第一步", "第二步"));
            MockMvc mvc = MockMvcBuilders
                    .standaloneSetup(new StateController(runs, plans))
                    .build();

            String body = mvc.perform(get("/runs/{runId}", run.runId()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode node = mapper.readTree(body);
            assertEquals("RUNNING", node.get("status").asText());
            assertEquals("整理本周任务", node.get("goal").asText());
            assertEquals(2, node.get("plans").get(0).get("steps").size());
            assertEquals("第一步",
                    node.get("plans").get(0).get("steps").get(0).get("description").asText());
        }
    }
}
