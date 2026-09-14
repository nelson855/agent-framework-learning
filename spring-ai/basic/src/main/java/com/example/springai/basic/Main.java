package com.example.springai.basic;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.credential.BearerTokenCredential;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 真实模型演示入口。配置只从 {@code .env} 文件读取，不读环境变量，
 * 详见 {@link ModelConfig}。仓库根目录有 {@code .env.example} 模板，
 * 复制为 {@code .env} 并填入 {@code CONFIG_AGENT_MODEL_API_KEY} 后运行。
 *
 * <p>无有效配置时直接退出并提示，不会伪造演示。
 */
public class Main {

    private static final String DEMO_INPUT =
            "创建一个“学习 Spring AI Advisor”的任务，然后查询这个任务并告诉我任务编号和状态。";

    public static void main(String[] args) {
        ModelConfig config = ModelConfig.load();

        // 对照手写版 OpenAiCompatibleLlmClient（手写 HTTP 拼请求、拼工具参数）：
        // 现在地址、Key、超时配进 builder 即可，请求格式、工具 schema 序列化都由框架处理。
        // 注意 2.0 要求同步和异步两个客户端同时就绪，只配一个会在运行时报错。
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

        try (TaskRepository repository = TaskRepository.fileBased("data/spring-ai-basic.db")) {
            TaskService taskService = new TaskService(repository);
            TaskAgent agent = new TaskAgent(chatModel, taskService);
            AgentResult result = agent.chat(articulate(args));
            System.out.println("---- call sequence ----");
            System.out.println("model turn 1: user input -> " + result.userInput());
            int turn = 1;
            for (ToolCallTrace trace : result.toolCalls()) {
                turn++;
                System.out.println("model turn " + turn + ": -> " + trace.toolName()
                        + "(" + trace.arguments() + ") -> tool result: " + trace.result());
            }
            turn++;
            System.out.println("model turn " + turn + ": -> final answer: " + result.finalAnswer());
        }
    }

    private static String articulate(String[] args) {
        if (args != null && args.length > 0 && !args[0].isBlank()) {
            return String.join(" ", args);
        }
        return DEMO_INPUT;
    }
}
