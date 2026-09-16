package com.example.springai.stateful;

import com.example.springai.stateful.agent.Planner;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Planner：桩模型输出 JSON，能解析出步骤清单。 */
class PlannerTest {

    static class StubChatModel implements ChatModel {
        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage("{\"steps\":[\"查任务\",\"建任务\"]}"))));
        }
    }

    @Test
    void parsesStepsFromModelJson() {
        Planner planner = new Planner(new StubChatModel());

        List<String> steps = planner.draftSteps("整理本周任务");

        assertEquals(List.of("查任务", "建任务"), steps);
    }
}
