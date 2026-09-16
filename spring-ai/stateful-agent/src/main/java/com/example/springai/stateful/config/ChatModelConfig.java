package com.example.springai.stateful.config;

import com.example.springai.stateful.agent.MemoryService;
import com.example.springai.stateful.agent.TaskAgentService;
import com.example.springai.stateful.agent.TaskTools;
import com.example.springai.stateful.domain.MemoryRepository;
import com.example.springai.stateful.domain.MessageRepository;
import com.example.springai.stateful.domain.TaskRepository;
import com.example.springai.stateful.domain.TaskService;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.credential.BearerTokenCredential;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 手工装配：本地仓库没有 spring-ai-starter，用 @Bean 把框架零件接起来。
 *
 * <p>Boot 只管依赖注入，ChatModel 怎么造仍显式写在这里，教学上看得见。
 */
@Configuration
public class ChatModelConfig {

    // @FW-CMP [HTTP] HTTP 客户端与请求/响应序列化
    //   手写（手写版 OpenAiCompatibleLlmClient.chat）：
    //     HttpRequest req = HttpRequest.newBuilder()
    //         .uri(URI.create(baseUrl + "/chat/completions"))
    //         .header("Authorization", "Bearer " + apiKey)
    //         .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
    //         .build();
    //     // 手动发 HTTP、手动解析 /choices/0/message/content
    //   框架（下方 @Bean）：
    //     OpenAiChatModel.builder().openAiClient(...).build();
    //     // 地址/Key/超时配进去即可，HTTP 细节和 JSON 序列化全收进框架
    //   差异：不再写 HTTP；代价是 Spring AI 2.0 要求同步 + 异步两个客户端都给。
    //   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#8-http
    @Bean
    public ChatModel chatModel() {
        ModelConfig config = ModelConfig.load();
        return OpenAiChatModel.builder()
                .openAiClient(OpenAIOkHttpClient.builder()
                        .baseUrl(config.baseUrl())
                        .credential(BearerTokenCredential.create(config.apiKey()))
                        .timeout(config.timeout())
                        .build())
                .openAiClientAsync(OpenAIOkHttpClientAsync.builder()
                        .baseUrl(config.baseUrl())
                        .credential(BearerTokenCredential.create(config.apiKey()))
                        .timeout(config.timeout())
                        .build())
                .options(OpenAiChatOptions.builder().model(config.model()).build())
                .build();
    }

    /** 模型上下文窗口：内存实现，重启就丢。UI History 另由 MessageRepository 落库。 */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .build();
    }

    @Bean
    public TaskRepository taskRepository() {
        return new TaskRepository("jdbc:sqlite:data/spring-ai-stateful.db");
    }

    @Bean
    public TaskService taskService(TaskRepository taskRepository) {
        return new TaskService(taskRepository);
    }

    @Bean
    public TaskTools taskTools(TaskService taskService) {
        return new TaskTools(taskService);
    }

    @Bean
    public MemoryRepository memoryRepository() {
        return new MemoryRepository("jdbc:sqlite:data/spring-ai-stateful.db");
    }

    @Bean
    public MemoryService memoryService(MemoryRepository memoryRepository) {
        return new MemoryService(memoryRepository);
    }

    @Bean
    public MessageRepository messageRepository() {
        return new MessageRepository("jdbc:sqlite:data/spring-ai-stateful.db");
    }

    @Bean
    public TaskAgentService taskAgentService(
            ChatModel chatModel,
            ChatMemory chatMemory,
            TaskTools taskTools,
            MemoryService memoryService,
            MessageRepository messageRepository) {
        return new TaskAgentService(chatModel, chatMemory, taskTools, memoryService, messageRepository);
    }
}
