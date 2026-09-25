package com.example.langchain4j.stateful;

import com.example.langchain4j.stateful.agent.MemoryService;
import com.example.langchain4j.stateful.agent.TaskAgentService;
import com.example.langchain4j.stateful.agent.TaskTools;
import com.example.langchain4j.stateful.domain.MemoryRepository;
import com.example.langchain4j.stateful.domain.MessageRepository;
import com.example.langchain4j.stateful.domain.TaskRepository;
import com.example.langchain4j.stateful.domain.TaskService;
import com.example.langchain4j.stateful.support.FakeChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话隔离：两个会话各进各的记忆窗口，互不串话；
 * 界面历史各自落库，可审计。
 */
class ConversationMemoryTest {

    @Test
    void windowsAreIsolatedByConversation() {
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory()) {
            TaskAgentService agent = new TaskAgentService(
                    FakeChatModel.answering("收到"),
                    new TaskTools(new TaskService(tasks)),
                    new MemoryService(memories), messages);
            String first = messages.createConversation("u1");
            String second = messages.createConversation("u1");

            agent.chat(first, "u1", "帮我记住，我喜欢早上学习");
            agent.chat(second, "u1", "你好");

            assertNotEquals(agent.inspectWindow(first), agent.inspectWindow(second));
            assertTrue(agent.inspectWindow(first).toString().contains("早上学习"));
        }
    }

    @Test
    void historyIsPersistedPerConversation() {
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory()) {
            TaskAgentService agent = new TaskAgentService(
                    FakeChatModel.answering("收到"),
                    new TaskTools(new TaskService(tasks)),
                    new MemoryService(memories), messages);
            String first = messages.createConversation("u1");
            String second = messages.createConversation("u1");

            agent.chat(first, "u1", "第一段对话");
            agent.chat(second, "u1", "第二段对话");

            assertEquals(2, messages.listMessages(first).size());
            assertEquals(2, messages.listMessages(second).size());
            assertTrue(messages.listMessages(first).get(0).content().contains("第一段对话"));
        }
    }

    @Test
    void longTermMemoryIsInjected() {
        try (TaskRepository tasks = TaskRepository.inMemory();
             MessageRepository messages = MessageRepository.inMemory();
             MemoryRepository memories = MemoryRepository.inMemory()) {
            MemoryService memoryService = new MemoryService(memories);
            memoryService.remember("u1", "学习时间", "早上学习效率高");
            FakeChatModel model = FakeChatModel.answering("收到");
            TaskAgentService agent = new TaskAgentService(
                    model, new TaskTools(new TaskService(tasks)), memoryService, messages);
            String conversationId = messages.createConversation("u1");

            agent.chat(conversationId, "u1", "学习");

            assertEquals(2, messages.listMessages(conversationId).size());
            assertTrue(messages.listMessages(conversationId).get(1).content().contains("收到"));
            assertTrue(agent.inspectWindow(conversationId).toString().contains("长期记忆"));
            assertTrue(agent.inspectWindow(conversationId).toString().contains("早上学习效率高"));
        }
    }
}
