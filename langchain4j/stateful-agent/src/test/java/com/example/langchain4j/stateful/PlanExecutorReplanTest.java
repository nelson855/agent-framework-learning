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
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PlanExecutor 重规划：某步失败出新计划；一直失败就认输。 */
class PlanExecutorReplanTest {

    /** 第一次调模型就炸、之后正常的桩，模拟某步执行失败。 */
    static final class FlakyModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("boom");
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("收到")).build();
        }
    }

    private PlanExecutor executorWith(
            ChatModel agentModel, PlanRepository plans, AgentRunRepository runs,
            TaskRepository tasks, MessageRepository messages, MemoryRepository memories) {
        TaskAgentService agent = new TaskAgentService(
                agentModel, new TaskTools(new TaskService(tasks)),
                new MemoryService(memories), messages);
        Planner planner = new Planner(
                FakeChatModel.answering("{\"steps\":[\"第一步\",\"第二步\"]}"));
        Replanner replanner = new Replanner(
                FakeChatModel.answering("{\"steps\":[\"新第一步\"]}"));
        return new PlanExecutor(planner, replanner, agent, plans, runs);
    }

    @Test
    void replansOnceThenSucceeds() {
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory();
             PlanRepository plans = PlanRepository.inMemory();
             AgentRunRepository runs = AgentRunRepository.inMemory()) {
            PlanExecutor executor =
                    executorWith(new FlakyModel(), plans, runs, tasks, messages, memories);
            String conversationId = messages.createConversation("u1");

            AgentRun run = executor.execute(conversationId, "u1", "整理本周任务");

            assertEquals(RunStatus.DONE, run.status());
            assertEquals(2, plans.findByRunId(run.runId()).size());
        }
    }

    @Test
    void givesUpAfterMaxReplans() {
        ChatModel alwaysBroken = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                throw new IllegalStateException("always broken");
            }
        };
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory();
             PlanRepository plans = PlanRepository.inMemory();
             AgentRunRepository runs = AgentRunRepository.inMemory()) {
            PlanExecutor executor =
                    executorWith(alwaysBroken, plans, runs, tasks, messages, memories);
            String conversationId = messages.createConversation("u1");

            assertThrows(IllegalStateException.class,
                    () -> executor.execute(conversationId, "u1", "整理本周任务"));
        }
    }
}
