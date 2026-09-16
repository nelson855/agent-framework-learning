package com.example.springai.stateful.web;

import com.example.springai.stateful.domain.MessageRepository;
import com.example.springai.stateful.web.dto.ConversationDto;
import com.example.springai.stateful.web.dto.CreateConversationRequest;
import com.example.springai.stateful.web.dto.MessageDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 会话管理：建会话、查消息流水账。不碰模型。 */
@RestController
@RequestMapping("/conversations")
public class ConversationController {

    private final MessageRepository messages;

    public ConversationController(MessageRepository messages) {
        this.messages = messages;
    }

    @PostMapping
    public ConversationDto create(@RequestBody CreateConversationRequest request) {
        String id = messages.createConversation(request.userId());
        return new ConversationDto(id, request.userId());
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> listMessages(@PathVariable("id") String conversationId) {
        return messages.listMessages(conversationId).stream()
                .map(m -> new MessageDto(m.id(), m.role(), m.content()))
                .toList();
    }
}
