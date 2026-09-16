package com.example.springai.stateful;

import com.example.springai.stateful.agent.Replanner;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Replanner：某步失败后，桩模型给出新 JSON，能解析出新步骤清单。 */
class ReplannerTest {

    static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage("{\"steps\":[\"换个关键词查\",\"重试建任务\"]}"))));
        }
    }

    @Test
    void parsesNewStepsAfterFailure() {
        Replanner replanner = new Replanner(new StubChatModel());

        List<String> steps = replanner.replan("整理本周任务", "查任务", "任务不存在");

        assertEquals(List.of("换个关键词查", "重试建任务"), steps);
    }
}
