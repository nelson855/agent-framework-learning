package com.example.springai.advanced;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.credential.BearerTokenCredential;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 真实模型演示入口。配置只从 {@code .env} 文件读取，不读环境变量，
 * 详见 {@link ModelConfig}。仓库根目录有 {@code .env.example} 模板，
 * 复制为 {@code .env} 并填入 {@code CONFIG_AGENT_MODEL_API_KEY} 后运行。
 *
 * <p>@FW-CMP 本类对应手写版 {@code agent-harness/Main}（装配 + 启动）。
 * 手写版要手动拼 HTTP 客户端、HarnessService 全套依赖；
 * 本类只剩"装模型 + 装知识库 + 启 MCP 子进程 + 调一次 chat()"。
 * MCP 子进程的启停（StdioProcessTransport.spawn/close）是本章新增的装配责任，
 * 框架不管进程生命周期。
 *
 * <p>无有效配置时直接退出并提示，不会伪造演示。
 * 完整对比见 {@code docs/comparisons/ch04_spring_ai_advanced.md}。
 */
public class Main {

    private static final String DEMO_INPUT =
            "根据规范，BLOCKED 任务应该怎么处理？另外帮我查一下 T-2 任务的当前状态。";

    public static void main(String[] args) {
        ModelConfig config = ModelConfig.load();

        ChatModel chatModel = OpenAiChatModel.builder()
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

        EmbeddingModel embeddingModel = new KeywordEmbeddingModel();
        PolicyKnowledgeBase knowledgeBase = new PolicyKnowledgeBase(embeddingModel);

        try (TaskMcpClient mcpClient = new TaskMcpClient(StdioProcessTransport.spawn())) {
            AdvancedAgent agent = new AdvancedAgent(chatModel, knowledgeBase, mcpClient);
            AdvancedAgent.AgentResult result = agent.chat(articulate(args));
            System.out.println("---- run trace ----");
            System.out.println(result.trace().format());
            System.out.println("---- final answer ----");
            System.out.println(result.finalAnswer());
        }
    }

    private static String articulate(String[] args) {
        if (args != null && args.length > 0 && !args[0].isBlank()) {
            return String.join(" ", args);
        }
        return DEMO_INPUT;
    }
}
