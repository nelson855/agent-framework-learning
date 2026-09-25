package com.example.langchain4j.stateful;

import com.example.langchain4j.stateful.agent.MemoryService;
import com.example.langchain4j.stateful.agent.PlanExecutor;
import com.example.langchain4j.stateful.agent.Planner;
import com.example.langchain4j.stateful.agent.Replanner;
import com.example.langchain4j.stateful.agent.TaskAgentService;
import com.example.langchain4j.stateful.agent.TaskTools;
import com.example.langchain4j.stateful.domain.AgentRun;
import com.example.langchain4j.stateful.domain.AgentRunRepository;
import com.example.langchain4j.stateful.domain.MemoryRepository;
import com.example.langchain4j.stateful.domain.MessageRepository;
import com.example.langchain4j.stateful.domain.PlanRepository;
import com.example.langchain4j.stateful.domain.RunStatus;
import com.example.langchain4j.stateful.domain.TaskRepository;
import com.example.langchain4j.stateful.domain.TaskService;
import com.example.langchain4j.stateful.support.FakeChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PlanExecutor：完整跑一个 3 步计划，状态推进并落库。 */
class PlanExecutorTest {

    @Test
    void runsThreeStepsToDone() {
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory();
             PlanRepository plans = PlanRepository.inMemory();
             AgentRunRepository runs = AgentRunRepository.inMemory()) {
            TaskAgentService agent = new TaskAgentService(
                    FakeChatModel.answering("收到"),
                    new TaskTools(new TaskService(tasks)),
                    new MemoryService(memories), messages);
            Planner planner = new Planner(
                    FakeChatModel.answering("{\"steps\":[\"第一步\",\"第二步\",\"第三步\"]}"));
            Replanner replanner = new Replanner(FakeChatModel.answering("{\"steps\":[]}"));
            PlanExecutor executor = new PlanExecutor(planner, replanner, agent, plans, runs);
            String conversationId = messages.createConversation("u1");

            AgentRun run = executor.execute(conversationId, "u1", "整理本周任务");

            assertEquals(RunStatus.DONE, run.status());
            assertEquals(3, run.currentStep());
            assertEquals(6, messages.listMessages(conversationId).size());
        }
    }
}
