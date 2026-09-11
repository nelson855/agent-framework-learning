package com.example.agentlearning.stage03;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 {@code .env} 文件读取配置（KEY=VALUE），作为环境变量的本地兜底。
 * 取值优先级：环境变量 > .env 文件。
 */
public final class EnvFile {

    private static volatile Map<String, String> fileCache;

    private EnvFile() {
    }

    public static String get(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return fileValues().get(key);
    }

    public static Map<String, String> parse(String content) {
        Map<String, String> values = new HashMap<>();
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            values.put(trimmed.substring(0, eq).trim(), unquote(trimmed.substring(eq + 1).trim()));
        }
        return values;
    }

    private static Map<String, String> fileValues() {
        Map<String, String> cached = fileCache;
        if (cached == null) {
            cached = loadFromFile();
            fileCache = cached;
        }
        return cached;
    }

    private static Map<String, String> loadFromFile() {
        Path dotEnv = findDotEnv();
        if (dotEnv == null) {
            return Map.of();
        }
        try {
            List<String> lines = Files.readAllLines(dotEnv, StandardCharsets.UTF_8);
            return parse(String.join("\n", lines));
        } catch (IOException e) {
            throw new IllegalStateException("读取 .env 失败: " + dotEnv, e);
        }
    }

    private static Path findDotEnv() {
        Path dir = Paths.get("").toAbsolutePath().normalize();
        while (dir != null) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}