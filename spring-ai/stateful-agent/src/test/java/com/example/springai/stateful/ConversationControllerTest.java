package com.example.springai.stateful;

import com.example.springai.stateful.domain.MessageRepository;
import com.example.springai.stateful.web.ConversationController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 会话接口：能建会话，能查到会话里的消息。 */
class ConversationControllerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createsConversationAndListsMessages() throws Exception {
        try (MessageRepository messages = MessageRepository.inMemory()) {
            MockMvc mvc = MockMvcBuilders
                    .standaloneSetup(new ConversationController(messages))
                    .build();

            String created = mvc.perform(post("/conversations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"u1\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode node = mapper.readTree(created);
            assertEquals("u1", node.get("userId").asText());
            String conversationId = node.get("id").asText();

            messages.appendMessage(conversationId, "user", "你好");

            String listed = mvc.perform(get("/conversations/{id}/messages", conversationId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode array = mapper.readTree(listed);
            assertEquals(1, array.size());
            assertEquals("你好", array.get(0).get("content").asText());
        }
    }
}
