package com.example.langchain4j.stateful.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 模型配置：从 {@code .env} 读连接信息。纯配置加载，不感知框架。
 *
 * <p>查找顺序：当前工作目录 → 模块目录 → 仓库根目录。键名与 Spring AI 版一致，
 * 方便同一份 {@code .env} 跑两个版本。
 */
public record ModelConfig(String baseUrl, String apiKey, String model, Duration timeout) {

    public static ModelConfig load() {
        Map<String, String> env = loadEnvFile();
        String baseUrl = firstNonBlank(
                env.get("OPENAI_BASE_URL"), System.getenv("OPENAI_BASE_URL"), "https://api.openai.com/v1");
        String apiKey = firstNonBlank(env.get("OPENAI_API_KEY"), System.getenv("OPENAI_API_KEY"), "");
        String model = firstNonBlank(
                env.get("OPENAI_MODEL"), System.getenv("OPENAI_MODEL"), "gpt-4o-mini");
        return new ModelConfig(baseUrl, apiKey, model, Duration.ofSeconds(60));
    }

    /** 没配 key 就算未配置，应用以降级离线模式启动，只演示页面和状态流转。 */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.strip();
            }
        }
        return "";
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> values = new HashMap<>();
        for (Path path : candidateEnvPaths()) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(path)) {
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }
                    int index = trimmed.indexOf('=');
                    values.putIfAbsent(
                            trimmed.substring(0, index).strip(), trimmed.substring(index + 1).strip());
                }
            } catch (IOException ignored) {
            }
        }
        return values;
    }

    private static Path[] candidateEnvPaths() {
        Path working = Path.of("").toAbsolutePath();
        Path module = working;
        Path probe = working;
        while (probe != null && !Files.isRegularFile(probe.resolve("TASKS.md"))) {
            probe = probe.getParent();
        }
        Path root = probe == null ? working : probe;
        return new Path[] {
            working.resolve(".env"),
            module.resolve("langchain4j/stateful-agent/.env"),
            root.resolve(".env"),
        };
    }
}
