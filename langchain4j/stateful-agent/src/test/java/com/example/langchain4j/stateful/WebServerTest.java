package com.example.langchain4j.stateful;

import com.example.langchain4j.stateful.agent.MemoryService;
import com.example.langchain4j.stateful.agent.PlanExecutor;
import com.example.langchain4j.stateful.agent.Planner;
import com.example.langchain4j.stateful.agent.Replanner;
import com.example.langchain4j.stateful.agent.TaskAgentService;
import com.example.langchain4j.stateful.agent.TaskTools;
import com.example.langchain4j.stateful.domain.AgentRunRepository;
import com.example.langchain4j.stateful.domain.MemoryRepository;
import com.example.langchain4j.stateful.domain.MessageRepository;
import com.example.langchain4j.stateful.domain.PlanRepository;
import com.example.langchain4j.stateful.domain.TaskRepository;
import com.example.langchain4j.stateful.domain.TaskService;
import com.example.langchain4j.stateful.support.FakeChatModel;
import com.example.langchain4j.stateful.web.WebServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WebServer：建会话 → 发目标跑完计划 → 查运行状态，全链路打通。 */
class WebServerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebServer startServer() throws Exception {
        TaskRepository tasks = new TaskRepository("jdbc:sqlite::memory:");
        MessageRepository messages = new MessageRepository("jdbc:sqlite::memory:");
        MemoryRepository memories = new MemoryRepository("jdbc:sqlite::memory:");
        PlanRepository plans = new PlanRepository("jdbc:sqlite::memory:");
        AgentRunRepository runs = new AgentRunRepository("jdbc:sqlite::memory:");
        FakeChatModel model = new FakeChatModel(
                "{\"steps\":[\"第一步\",\"第二步\"]}", "收到", "收到");
        TaskAgentService agent = new TaskAgentService(
                model, new TaskTools(new TaskService(tasks)),
                new MemoryService(memories), messages);
        PlanExecutor executor = new PlanExecutor(
                new Planner(model), new Replanner(model), agent, plans, runs);
        WebServer server = new WebServer(
                0, executor, agent, new MemoryService(memories), messages, plans, runs);
        server.start();
        return server;
    }

    private static String post(HttpClient client, String url, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String get(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    @Test
    void chatFlowRunsPlanAndExposesState() throws Exception {
        WebServer server = startServer();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String conversationId =
                    MAPPER.readTree(post(client, base + "/conversations", "{\"userId\":\"u1\"}"))
                            .get("id").asText();
            String runId = MAPPER.readTree(post(client,
                            base + "/conversations/" + conversationId + "/chat",
                            "{\"userId\":\"u1\",\"goal\":\"整理本周任务\"}"))
                    .get("runId").asText();

            JsonNode state = MAPPER.readTree(get(client, base + "/runs/" + runId));

            assertEquals("DONE", state.get("run").get("status").asText());
            assertEquals(2, state.get("run").get("currentStep").asInt());
            assertEquals(1, state.get("plans").size());
            assertEquals(2, state.get("plans").get(0).get("steps").size());
        } finally {
            server.stop();
        }
    }

    @Test
    void debugEndpointShowsWindowAndToolCalls() throws Exception {
        WebServer server = startServer();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String base = "http://localhost:" + server.port();
            String conversationId =
                    MAPPER.readTree(post(client, base + "/conversations", "{\"userId\":\"u1\"}"))
                            .get("id").asText();
            post(client, base + "/conversations/" + conversationId + "/chat",
                    "{\"userId\":\"u1\",\"goal\":\"整理本周任务\"}");

            JsonNode debug = MAPPER.readTree(get(client,
                    base + "/debug/conversations/" + conversationId + "?userId=u1&q="));

            assertTrue(debug.get("messages").size() >= 2);
            assertTrue(debug.get("window").isArray());
        } finally {
            server.stop();
        }
    }
}
