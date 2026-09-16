package com.example.springai.stateful;

import com.example.springai.stateful.agent.MemoryService;
import com.example.springai.stateful.agent.PlanExecutor;
import com.example.springai.stateful.agent.Planner;
import com.example.springai.stateful.agent.Replanner;
import com.example.springai.stateful.agent.TaskAgentService;
import com.example.springai.stateful.agent.TaskTools;
import com.example.springai.stateful.domain.AgentRunRepository;
import com.example.springai.stateful.domain.MemoryRepository;
import com.example.springai.stateful.domain.MessageRepository;
import com.example.springai.stateful.domain.PlanRepository;
import com.example.springai.stateful.domain.RunStatus;
import com.example.springai.stateful.domain.TaskRepository;
import com.example.springai.stateful.domain.TaskService;
import com.example.springai.stateful.web.ChatController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 对话接口：发一次目标，返回运行编号，且运行最终完成。 */
class ChatControllerTest {

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

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void startsRunAndReturnsRunId() throws Exception {
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory();
             PlanRepository plans = PlanRepository.inMemory();
             AgentRunRepository runs = AgentRunRepository.inMemory()) {
            ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
            TaskAgentService agent = new TaskAgentService(
                    new JsonChatModel("收到"), chatMemory,
                    new TaskTools(new TaskService(tasks)),
                    new MemoryService(memories), messages);
            Planner planner = new Planner(new JsonChatModel("{\"steps\":[\"第一步\"]}"));
            Replanner replanner = new Replanner(new JsonChatModel("{\"steps\":[]}"));
            PlanExecutor executor = new PlanExecutor(planner, replanner, agent, plans, runs);
            MockMvc mvc = MockMvcBuilders
                    .standaloneSetup(new ChatController(executor))
                    .build();
            String conversationId = messages.createConversation("u1");

            String body = mvc.perform(post("/conversations/{id}/chat", conversationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"u1\",\"goal\":\"整理本周任务\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode node = mapper.readTree(body);
            String runId = node.get("runId").asText();
            assertEquals(RunStatus.DONE, runs.findById(runId).orElseThrow().status());
        }
    }
}
