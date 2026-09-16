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
 * <p>@FW-CMP 本类对应手写版 {@code agent-harness/Main}（装配 + 启动）。
 * 手写版要手动拼 HTTP 客户端、手动构造 HarnessService 全套依赖
 * （Planner / ContextBuilder / ToolExecutor / Approval / Checkpoint / Trace / Evaluator），
 * 本类只剩"装模型 + 装业务仓库 + 调一次 chat()"。
 *
 * <p>无有效配置时直接退出并提示，不会伪造演示。
 * 完整对比见 {@code docs/comparisons/ch02_spring_ai_basic.md}。
 */
public class Main {

    private static final String DEMO_INPUT =
            "创建一个“学习 Spring AI Advisor”的任务，然后查询这个任务并告诉我任务编号和状态。";

    public static void main(String[] args) {
        ModelConfig config = ModelConfig.load();

        // @FW-CMP [HTTP] HTTP 客户端与请求/响应序列化
        //   手写（agent-harness/OpenAiCompatibleLlmClient.chat）：
        //     Map<String, Object> body = new LinkedHashMap<>();
        //     body.put("model", model);
        //     body.put("messages", messages);
        //     HttpRequest req = HttpRequest.newBuilder()
        //         .uri(URI.create(baseUrl + "/chat/completions"))
        //         .header("Authorization", "Bearer " + apiKey)
        //         .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
        //         .build();
        //     // 手动发 HTTP、手动解析 /choices/0/message/content
        //   框架（下方 builder）：
        //     OpenAiChatModel.builder().openAiClient(...).build();
        //     // 地址/Key/超时配进去即可，HTTP 细节和 JSON 序列化全收进框架
        //   差异：不再写 HTTP；代价是要引入 OpenAI Java SDK 依赖，
        //   且 Spring AI 2.0 要求同步 + 异步两个客户端都给。
        //   完整版见 docs/comparisons/ch02_spring_ai_basic.md#3-http
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
