package com.example.langchain4j.stateful;

import com.example.langchain4j.stateful.agent.Planner;
import com.example.langchain4j.stateful.config.ChatModelFactory;
import com.example.langchain4j.stateful.config.ModelConfig;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ChatModelFactory：没配 key 时降级离线模型，页面和状态流转照样能演示。 */
class ChatModelFactoryTest {

    @Test
    void offlineModelAnswersPlansAsJson() {
        ChatModel offline = ChatModelFactory.offline();

        List<String> steps = new Planner(offline).draftSteps("整理本周任务");

        assertEquals(List.of("理解目标", "执行任务", "确认结果"), steps);
    }

    @Test
    void offlineModelAnswersChatAsText() {
        ChatModel offline = ChatModelFactory.offline();

        String answer = offline.chat("你好");

        assertTrue(answer.contains("离线模式"));
    }

    @Test
    void emptyApiKeyMeansNotConfigured() {
        ModelConfig config = new ModelConfig("https://api.openai.com/v1", "", "gpt-4o-mini",
                java.time.Duration.ofSeconds(60));

        assertFalse(config.isConfigured());
    }
}
