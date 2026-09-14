package com.example.springai.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/**
 * 边界/配置测试：不依赖真实模型。
 *
 * <p>1) 两个工具能被框架解析成 ToolCallback（名称即模型看到的名字）；
 * 2) ChatClient 接上桩模型能跑通一次调用并原样返回最终回答，
 *    证明业务代码里没有手写循环也能完成一次调用。
 */
class ChatClientWiringTest {

    private TaskRepository repository;

    @BeforeEach
    void setUp() {
        repository = TaskRepository.inMemory();
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void toolsResolveToTwoNamedCallbacks() {
        TaskTools tools = new TaskTools(new TaskService(repository), trace -> { });

        ToolCallback[] callbacks = ToolCallbacks.from(tools);

        List<String> names = java.util.Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .sorted()
                .toList();
        assertEquals(List.of("createTask", "getTask"), names);
    }

    @Test
    void agentReturnsStubbedFinalAnswerWithoutRealModel() {
        ChatModel stub = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("stubbed final"))));
            }
        };
        TaskAgent agent = new TaskAgent(stub, new TaskService(repository));

        AgentResult result = agent.chat("创建一个任务");

        assertEquals("stubbed final", result.finalAnswer());
        assertTrue(result.toolCalls().isEmpty(), "stub makes no tool calls, so the loop ends immediately");
    }
}
