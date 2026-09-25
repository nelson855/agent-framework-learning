package com.example.langchain4j.stateful.support;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 测试桩模型：按预设队列返回固定文本，不联网。
 * 队列用完后重复最后一条，方便多步计划测试。
 */
public class FakeChatModel implements ChatModel {

    private final Deque<String> replies = new ArrayDeque<>();
    private String last = "";
    private int calls;

    public FakeChatModel(String... replies) {
        this.replies.addAll(List.of(replies));
    }

    public static FakeChatModel answering(String reply) {
        return new FakeChatModel(reply);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        calls++;
        if (!replies.isEmpty()) {
            last = replies.poll();
        }
        return ChatResponse.builder().aiMessage(AiMessage.from(last)).build();
    }

    public int calls() {
        return calls;
    }
}
