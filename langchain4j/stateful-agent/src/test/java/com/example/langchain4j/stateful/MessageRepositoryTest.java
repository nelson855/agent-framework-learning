package com.example.langchain4j.stateful;

import com.example.langchain4j.stateful.domain.Message;
import com.example.langchain4j.stateful.domain.MessageRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MessageRepository：会话流水完整可查，空会话返回空表。 */
class MessageRepositoryTest {

    @Test
    void appendsAndListsInOrder() {
        try (MessageRepository repository = MessageRepository.inMemory()) {
            String conversationId = repository.createConversation("u1");
            repository.appendMessage(conversationId, "user", "你好");
            repository.appendMessage(conversationId, "assistant", "收到");

            List<Message> history = repository.listMessages(conversationId);

            assertEquals(2, history.size());
            assertEquals("user", history.get(0).role());
            assertTrue(history.get(1).content().contains("收到"));
        }
    }

    @Test
    void unknownConversationGivesEmptyHistory() {
        try (MessageRepository repository = MessageRepository.inMemory()) {
            assertTrue(repository.listMessages("no-such-id").isEmpty());
        }
    }
}
