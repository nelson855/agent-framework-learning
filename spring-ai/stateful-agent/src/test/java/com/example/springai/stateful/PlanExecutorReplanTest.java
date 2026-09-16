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
import com.example.springai.stateful.domain.Plan;
import com.example.springai.stateful.domain.PlanRepository;
import com.example.springai.stateful.domain.PlanStepStatus;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PlanExecutor 重规划：某步失败后调 Replanner 出新计划并跑完；一直失败则运行记 FAILED。 */
class PlanExecutorReplanTest {

    /** 第一次调抛错，之后正常回话。 */
    static class FlakyChatModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse call(Prompt prompt) {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("工具失败");
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("收到"))));
        }
    }

    /** 每次调都抛错。 */
    static class AlwaysFailingChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            throw new IllegalStateException("一直失败");
        }
    }

    static class JsonChatModel implements ChatModel {
        private final String json;

        JsonChatModel(String json) {
            this.json = json;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
        }
    }

    static class CountingReplanner extends Replanner {
        int calls;

        CountingReplanner(ChatModel chatModel) {
            super(chatModel);
        }

        @Override
        public java.util.List<String> replan(String goal, String failedStep, String failureReason) {
            calls++;
            return super.replan(goal, failedStep, failureReason);
        }
    }

    private PlanExecutor executorWith(
            ChatModel agentModel, CountingReplanner replanner,
            PlanRepository plans, AgentRunRepository runs,
            MessageRepository messages, TaskRepository tasks, MemoryRepository memories) {
        ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
        TaskAgentService agent = new TaskAgentService(
                agentModel, chatMemory,
                new TaskTools(new TaskService(tasks)),
                new MemoryService(memories), messages);
        Planner planner = new Planner(new JsonChatModel("{\"steps\":[\"第一步\",\"第二步\"]}"));
        return new PlanExecutor(planner, replanner, agent, plans, runs);
    }

    @Test
    void recoversWithNewPlanAfterStepFailure() {
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory();
             PlanRepository plans = PlanRepository.inMemory();
             AgentRunRepository runs = AgentRunRepository.inMemory()) {
            CountingReplanner replanner =
                    new CountingReplanner(new JsonChatModel("{\"steps\":[\"补救步骤\"]}"));
            PlanExecutor executor = executorWith(
                    new FlakyChatModel(), replanner, plans, runs, messages, tasks, memories);
            String conversationId = messages.createConversation("u1");

            AgentRun run = executor.execute(conversationId, "u1", "整理本周任务");

            assertEquals(1, replanner.calls);
            assertEquals(RunStatus.DONE, run.status());
            List<Plan> history = plans.findByRunId(run.runId());
            assertEquals(2, history.size());
            assertEquals(PlanStepStatus.FAILED, history.get(0).steps().get(0).status());
            assertEquals(PlanStepStatus.DONE, history.get(1).steps().get(0).status());
        }
    }

    @Test
    void marksRunFailedWhenReplansKeepFailing() {
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory();
             PlanRepository plans = PlanRepository.inMemory();
             AgentRunRepository runs = AgentRunRepository.inMemory()) {
            CountingReplanner replanner =
                    new CountingReplanner(new JsonChatModel("{\"steps\":[\"补救步骤\"]}"));
            PlanExecutor executor = executorWith(
                    new AlwaysFailingChatModel(),
                    replanner, plans, runs, messages, tasks, memories);
            String conversationId = messages.createConversation("u1");

            assertThrows(IllegalStateException.class,
                    () -> executor.execute(conversationId, "u1", "整理本周任务"));
            assertEquals(2, replanner.calls);
        }
    }
}
