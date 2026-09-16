package com.example.springai.stateful;

import com.example.springai.stateful.domain.MessageRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** MessageRepository：会话里的消息按写入顺序查回来（UI History 流水账）。 */
class MessageRepositoryTest {

    @Test
    void messagesComeBackInOrder() {
        try (MessageRepository repository = MessageRepository.inMemory()) {
            String conversationId = repository.createConversation("u1");
            repository.appendMessage(conversationId, "user", "帮我建个任务");
            repository.appendMessage(conversationId, "assistant", "好的，已记录");

            var messages = repository.listMessages(conversationId);

            assertEquals(2, messages.size());
            assertEquals("user", messages.get(0).role());
            assertEquals("帮我建个任务", messages.get(0).content());
            assertEquals("assistant", messages.get(1).role());
        }
    }
}
