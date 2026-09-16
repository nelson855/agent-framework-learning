package com.example.springai.stateful.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 项目级大模型配置，只从 {@code .env} 文件读取，不读环境变量。
 *
 * <p>查找顺序：当前工作目录的 {@code .env}，找不到就逐层往上找，
 * 直到含 {@code .git} 的仓库根目录。所以模块目录和仓库根目录各放一份都行，
 * 离运行位置近的那份先生效。仓库根目录有 {@code .env.example} 模板，
 * 复制为 {@code .env} 后填入真实值即可。
 *
 * <p>与 {@code spring-ai/basic} 的同名类完全同构，只是换了包名。
 */
public record ModelConfig(String baseUrl, String apiKey, String model, Duration timeout) {

    private static final Pattern TIMEOUT_PATTERN = Pattern.compile("^(\\d+)(ms|s|m|h)$");
    private static final String DEFAULT_TIMEOUT = "30s";

    /** 从 .env 文件加载，缺文件、缺键、值非法都直接抛错说明原因。 */
    public static ModelConfig load() {
        return load(Path.of(System.getProperty("user.dir")));
    }

    static ModelConfig load(Path workingDir) {
        Path dotEnv = locateDotEnv(workingDir.toAbsolutePath().normalize());
        if (dotEnv == null) {
            throw new IllegalStateException(
                    "Model config not found: no .env from " + workingDir + " up to the repository root. "
                            + "Copy .env.example to .env at the repository root and fill in CONFIG_AGENT_MODEL_API_KEY.");
        }
        Map<String, String> values = parseDotEnv(dotEnv);
        String baseUrl = values.get("CONFIG_AGENT_MODEL_BASE_URL");
        String apiKey = values.get("CONFIG_AGENT_MODEL_API_KEY");
        String model = values.get("CONFIG_AGENT_MODEL_NAME");
        String timeoutRaw = values.getOrDefault("CONFIG_AGENT_MODEL_TIMEOUT", DEFAULT_TIMEOUT);

        if (isBlank(baseUrl) || isBlank(apiKey) || isBlank(model)) {
            throw new IllegalStateException(
                    "Model config incomplete in " + dotEnv + ": "
                            + "CONFIG_AGENT_MODEL_BASE_URL / CONFIG_AGENT_MODEL_API_KEY / "
                            + "CONFIG_AGENT_MODEL_NAME must all be non-blank.");
        }
        return new ModelConfig(baseUrl.strip(), apiKey.strip(), model.strip(), parseTimeout(timeoutRaw.strip()));
    }

    /** 超时格式：正整数 + ms/s/m/h，例如 500ms、30s、2m、1h。 */
    static Duration parseTimeout(String raw) {
        Matcher matcher = TIMEOUT_PATTERN.matcher(raw.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Bad CONFIG_AGENT_MODEL_TIMEOUT: '" + raw + "', expected like 500ms / 30s / 2m / 1h.");
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Bad CONFIG_AGENT_MODEL_TIMEOUT: '" + raw + "', amount must be positive.");
        }
        return switch (matcher.group(2)) {
            case "ms" -> Duration.ofMillis(amount);
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> throw new IllegalArgumentException("Unreachable timeout unit: " + raw);
        };
    }

    static Map<String, String> parseDotEnv(Path dotEnv) {
        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(dotEnv)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    values.put(trimmed.substring(0, eq).strip(), trimmed.substring(eq + 1).strip());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Read model config failed: " + dotEnv, e);
        }
        return values;
    }

    private static Path locateDotEnv(Path dir) {
        Path current = dir;
        while (current != null) {
            if (Files.isRegularFile(current.resolve(".env"))) {
                return current.resolve(".env");
            }
            if (Files.isDirectory(current.resolve(".git"))) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
