package com.example.springai.stateful;

import com.example.springai.stateful.agent.MemoryService;
import com.example.springai.stateful.agent.PlanExecutor;
import com.example.springai.stateful.agent.Planner;
import com.example.springai.stateful.agent.Replanner;
import com.example.springai.stateful.agent.TaskAgentService;
import com.example.springai.stateful.agent.TaskTools;
import com.example.springai.stateful.domain.AgentRun;
import com.example.springai.stateful.domain.AgentRunRepository;
import com.example.springai.stateful.domain.MemoryRepository;
import com.example.springai.stateful.domain.MessageRepository;
import com.example.springai.stateful.domain.PlanRepository;
import com.example.springai.stateful.domain.RunStatus;
import com.example.springai.stateful.domain.TaskRepository;
import com.example.springai.stateful.domain.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PlanExecutor：完整跑一个 3 步计划，状态推进并落库。 */
class PlanExecutorTest {

    static class StubChatModel implements ChatModel {
        private final String reply;

        StubChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }

    @Test
    void runsThreeStepsToDone() {
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory();
             PlanRepository plans = PlanRepository.inMemory();
             AgentRunRepository runs = AgentRunRepository.inMemory()) {
            ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
            TaskAgentService agent = new TaskAgentService(
                    new StubChatModel("收到"), chatMemory,
                    new TaskTools(new TaskService(tasks)),
                    new MemoryService(memories), messages);
            Planner planner = new Planner(
                    new StubChatModel("{\"steps\":[\"第一步\",\"第二步\",\"第三步\"]}"));
            Replanner replanner = new Replanner(new StubChatModel("{\"steps\":[]}"));
            PlanExecutor executor = new PlanExecutor(planner, replanner, agent, plans, runs);
            String conversationId = messages.createConversation("u1");

            AgentRun run = executor.execute(conversationId, "u1", "整理本周任务");

            assertEquals(RunStatus.DONE, run.status());
            assertEquals(3, run.currentStep());
            assertEquals(6, messages.listMessages(conversationId).size());
        }
    }
}
