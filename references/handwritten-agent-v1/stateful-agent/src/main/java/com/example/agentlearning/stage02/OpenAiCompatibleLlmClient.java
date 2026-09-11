package com.example.agentlearning.stage02;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 兼容 OpenAI Chat Completions 协议的 LLM 客户端。
 *
 * <p>配置从环境变量或仓库根目录 {@code .env} 文件读取（环境变量优先）。
 */
public final class OpenAiCompatibleLlmClient implements LlmClient {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleLlmClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = requireNonBlank(baseUrl, "LLM_BASE_URL");
        this.apiKey = requireNonBlank(apiKey, "LLM_API_KEY");
        this.model = requireNonBlank(model, "LLM_MODEL");
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public static OpenAiCompatibleLlmClient fromConfig() {
        return new OpenAiCompatibleLlmClient(
                EnvFile.get("LLM_BASE_URL"),
                EnvFile.get("LLM_API_KEY"),
                EnvFile.get("LLM_MODEL"));
    }

    public static boolean isConfigured() {
        return EnvFile.get("LLM_BASE_URL") != null && !EnvFile.get("LLM_BASE_URL").isBlank()
                && EnvFile.get("LLM_API_KEY") != null && !EnvFile.get("LLM_API_KEY").isBlank()
                && EnvFile.get("LLM_MODEL") != null && !EnvFile.get("LLM_MODEL").isBlank();
    }

    @Override
    public LlmResponse chat(List<Message> messages) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody)))
                .build();

        HttpResponse<String> response = send(request);
        checkStatus(response);

        JsonNode root = parseJson(response.body());
        String content = root.at("/choices/0/message/content").asText();
        return new LlmResponse(content);
    }

    private String chatCompletionsUrl() {
        return baseUrl.replaceAll("/+$", "") + "/chat/completions";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("序列化 LLM 请求失败", e);
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("LLM 网络调用失败: " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM 调用被中断", e);
        }
    }

    private void checkStatus(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new IllegalStateException("解析 LLM 响应失败", e);
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少配置: " + name);
        }
        return value;
    }
}