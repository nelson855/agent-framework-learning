package com.example.springai.stateful;

import com.example.springai.stateful.agent.MemoryService;
import com.example.springai.stateful.agent.TaskAgentService;
import com.example.springai.stateful.agent.TaskTools;
import com.example.springai.stateful.domain.MemoryRepository;
import com.example.springai.stateful.domain.MessageRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 用桩模型验证接线：ChatMemory 正确挂上，UI History 正确落库。
 * 不连真实模型，全部离线。
 */
class ConversationMemoryTest {

    /** 桩模型：永远回固定一句话，不调工具。 */
    static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage("收到"))));
        }
    }

    @Test
    void chatPersistsHistoryAndUsesChatMemory() {
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory()) {
            ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
            TaskAgentService agent = new TaskAgentService(
                    new StubChatModel(), chatMemory,
                    new TaskTools(new TaskService(tasks)),
                    new MemoryService(memories), messages);
            String conversationId = messages.createConversation("u1");

            String reply = agent.chat(conversationId, "u1", "你好");

            assertEquals("收到", reply);
            assertEquals(2, messages.listMessages(conversationId).size());
            assertFalse(chatMemory.get(conversationId).isEmpty());
        }
    }
}
