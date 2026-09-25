package com.example.langchain4j.stateful.config;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * 模型装配：按配置造出框架的 {@code ChatModel}。
 *
 * <p>@FW-CMP [HTTP] HTTP 客户端不用手写
 *   手写（手写版 OpenAiCompatibleLlmClient）：
 *     HttpClient client = HttpClient.newHttpClient();
 *     HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
 *         .header("Authorization", "Bearer " + apiKey)
 *         .POST(BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
 *         .build();
 *     // 序列化、鉴权头、重试、超时、响应解析全自己写
 *   框架（下方 builder）：
 *     OpenAiChatModel.builder().baseUrl(...).apiKey(...).modelName(...).build()
 *     // HTTP 发送、鉴权、序列化、tool_calls 解析全在框架里
 *   差异：约 120 行 HTTP 样板消失；代价是请求细节（重试几次、超时多少、
 *   发了什么 JSON）藏进框架，排错靠开日志而不是读代码。
 *   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#8-http
 */
public final class ChatModelFactory {

    private ChatModelFactory() {
    }

    // @FW-CMP [CONFIG] 装配从手写工厂方法搬到框架 builder
    //   手写（手写版 AppComponents.build）：
    //     LlmClient llm = OpenAiCompatibleLlmClient.fromConfig();
    //     // 自己 new 客户端、自己传仓库、自己组装 Runner
    //   框架（下方）：
    //     OpenAiChatModel.builder().baseUrl(...).apiKey(...).modelName(...).build()
    //     // 模型实例仍由我们 new，但"怎么调 HTTP"不用我们管
    //   差异：装配代码还在（没用注入框架），消失的是 HTTP 细节。
    //   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#8-http
    public static ChatModel create(ModelConfig config) {
        return OpenAiChatModel.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .modelName(config.model())
                .timeout(config.timeout())
                .maxRetries(2)
                .build();
    }

    /** 离线演示用的模型：没配 key 时让页面和状态流转照样能跑，不联网。 */
    public static ChatModel offline() {
        return new OfflineChatModel();
    }

    /**
     * 最小的内存问答模型：只实现文本问答，不支持工具调用。
     * 注意它走的是 {@code ChatModel} 接口的默认方法路径，
     * 因此 AiServices 代理在离线模式下不会触发工具循环——
     * 这正好说明"工具循环是模型返回 tool_calls 才发生的"，不是代理无条件做的。
     */
    static final class OfflineChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            String lastUser = request.messages().stream()
                    .filter(m -> m instanceof UserMessage)
                    .map(m -> ((UserMessage) m).singleText())
                    .reduce((first, second) -> second)
                    .orElse("");
            String text;
            if (lastUser.contains("\"steps\"") || lastUser.contains("执行步骤")) {
                text = "{\"steps\":[\"理解目标\",\"执行任务\",\"确认结果\"]}";
            } else {
                text = "（离线模式）已收到：" + abbreviate(lastUser)
                        + "。配好 .env 后可接真实模型，观察右侧状态流转。";
            }
            return ChatResponse.builder()
                    .aiMessage(dev.langchain4j.data.message.AiMessage.from(text))
                    .build();
        }

        private static String abbreviate(String text) {
            if (text == null) {
                return "";
            }
            String flat = text.replaceAll("\\s+", " ");
            return flat.length() <= 60 ? flat : flat.substring(0, 60) + "…";
        }
    }
}
