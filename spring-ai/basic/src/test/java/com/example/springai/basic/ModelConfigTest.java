package com.example.springai.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ModelConfig 测试：只测文件解析与超时解析，不连真实模型。 */
class ModelConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void parseTimeoutSupportsAllUnits() {
        assertEquals(Duration.ofMillis(500), ModelConfig.parseTimeout("500ms"));
        assertEquals(Duration.ofSeconds(30), ModelConfig.parseTimeout("30s"));
        assertEquals(Duration.ofMinutes(2), ModelConfig.parseTimeout("2m"));
        assertEquals(Duration.ofHours(1), ModelConfig.parseTimeout("1h"));
    }

    @Test
    void parseTimeoutRejectsBadFormat() {
        assertThrows(IllegalArgumentException.class, () -> ModelConfig.parseTimeout("30"));
        assertThrows(IllegalArgumentException.class, () -> ModelConfig.parseTimeout("0s"));
        assertThrows(IllegalArgumentException.class, () -> ModelConfig.parseTimeout("abc"));
    }

    @Test
    void loadReadsDotEnvBesideWorkingDir() throws Exception {
        Files.writeString(tempDir.resolve(".env"), """
                # comment line
                CONFIG_AGENT_MODEL_BASE_URL=http://example/v1
                CONFIG_AGENT_MODEL_API_KEY=demo-key
                CONFIG_AGENT_MODEL_NAME=demo-model
                CONFIG_AGENT_MODEL_TIMEOUT=45s
                """);

        ModelConfig config = ModelConfig.load(tempDir);

        assertEquals("http://example/v1", config.baseUrl());
        assertEquals("demo-key", config.apiKey());
        assertEquals("demo-model", config.model());
        assertEquals(Duration.ofSeconds(45), config.timeout());
    }

    @Test
    void loadWalksUpToParentDotEnv() throws Exception {
        Files.writeString(tempDir.resolve(".env"), """
                CONFIG_AGENT_MODEL_BASE_URL=http://example/v1
                CONFIG_AGENT_MODEL_API_KEY=demo-key
                CONFIG_AGENT_MODEL_NAME=demo-model
                """);
        Path child = Files.createDirectory(tempDir.resolve("child"));

        ModelConfig config = ModelConfig.load(child);

        assertEquals("http://example/v1", config.baseUrl());
        assertEquals(Duration.ofSeconds(30), config.timeout(), "missing timeout falls back to 30s");
    }

    @Test
    void loadFailsClearlyWithoutDotEnv() throws Exception {
        Path child = Files.createDirectory(tempDir.resolve("empty"));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> ModelConfig.load(child));

        assertTrue(error.getMessage().contains(".env.example"));
    }

    @Test
    void loadFailsClearlyWithBlankApiKey() throws Exception {
        Files.writeString(tempDir.resolve(".env"), """
                CONFIG_AGENT_MODEL_BASE_URL=http://example/v1
                CONFIG_AGENT_MODEL_API_KEY=
                CONFIG_AGENT_MODEL_NAME=demo-model
                """);

        assertThrows(IllegalStateException.class, () -> ModelConfig.load(tempDir));
    }

    @Test
    void parseDotEnvSkipsCommentsAndBlankLines() throws Exception {
        Path dotEnv = tempDir.resolve(".env");
        Files.writeString(dotEnv, "# comment\n\nA=1\nB = 2 \n");

        Map<String, String> values = ModelConfig.parseDotEnv(dotEnv);

        assertEquals(Map.of("A", "1", "B", "2"), values);
    }
}
