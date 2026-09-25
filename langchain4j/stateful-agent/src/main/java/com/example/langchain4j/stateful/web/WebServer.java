package com.example.langchain4j.stateful.web;

import com.example.langchain4j.stateful.agent.MemoryService;
import com.example.langchain4j.stateful.agent.PlanExecutor;
import com.example.langchain4j.stateful.agent.TaskAgentService;
import com.example.langchain4j.stateful.agent.TaskTools;
import com.example.langchain4j.stateful.domain.AgentRun;
import com.example.langchain4j.stateful.domain.AgentRunRepository;
import com.example.langchain4j.stateful.domain.Memory;
import com.example.langchain4j.stateful.domain.Message;
import com.example.langchain4j.stateful.domain.MessageRepository;
import com.example.langchain4j.stateful.domain.Plan;
import com.example.langchain4j.stateful.domain.PlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调试台 Web 层：JDK 自带 HttpServer，只做 HTTP/JSON 转换，不实现 Agent 逻辑。
 *
 * <p>@FW-CMP [CONFIG] Web 层框架不管，自己用 JDK 现成的
 *   手写（手写版 WebMain）：
 *     // 同样是 com.sun.net.httpserver.HttpServer，自己分路由、自己写 JSON
 *   框架（本类）：
 *     // LangChain4j 不带 Web 能力，Spring AI 版用了 Spring Boot，
 *     // 本模块刻意沿用手写版的 JDK HttpServer：少一个重型依赖，
 *     // 顺带证明"换框架不用换 Web 层"
 *   差异：和手写版几乎一样；和 Spring AI 版比，少了整套 Boot 自动装配，
 *   代价是路由和参数解析全手写（见下面的小工具方法）。
 *   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#9-web
 */
public class WebServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final PlanExecutor executor;
    private final TaskAgentService agent;
    private final MemoryService memories;
    private final MessageRepository messages;
    private final PlanRepository plans;
    private final AgentRunRepository runs;

    public WebServer(
            int port,
            PlanExecutor executor,
            TaskAgentService agent,
            MemoryService memories,
            MessageRepository messages,
            PlanRepository plans,
            AgentRunRepository runs) throws IOException {
        this.executor = executor;
        this.agent = agent;
        this.memories = memories;
        this.messages = messages;
        this.plans = plans;
        this.runs = runs;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/conversations", this::routeConversations);
        this.server.createContext("/runs", this::routeRuns);
        this.server.createContext("/debug", this::routeDebug);
        this.server.createContext("/", this::routeStatic);
        this.server.setExecutor(null);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    // ---------------- 路由 ----------------

    private void routeConversations(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (path.equals("/conversations") && method.equalsIgnoreCase("POST")) {
            Map<String, Object> body = readJson(exchange);
            String userId = stringOf(body.getOrDefault("userId", "u1"));
            writeJson(exchange, 200, Map.of("id", messages.createConversation(userId)));
        } else if (path.matches("/conversations/[^/]+/messages") && method.equalsIgnoreCase("GET")) {
            String id = path.split("/")[2];
            List<Map<String, Object>> out = messages.listMessages(id).stream()
                    .map(m -> Map.<String, Object>of(
                            "id", m.id(), "role", m.role(), "content", m.content()))
                    .toList();
            writeJson(exchange, 200, Map.of("messages", out));
        } else if (path.matches("/conversations/[^/]+/chat") && method.equalsIgnoreCase("POST")) {
            String id = path.split("/")[2];
            Map<String, Object> body = readJson(exchange);
            String userId = stringOf(body.getOrDefault("userId", "u1"));
            String goal = stringOf(body.get("goal"));
            AgentRun run = executor.execute(id, userId, goal);
            writeJson(exchange, 200, Map.of("runId", run.runId()));
        } else {
            writeJson(exchange, 404, Map.of("error", "not found: " + path));
        }
    }

    private void routeRuns(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.matches("/runs/[^/]+") || !exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            writeJson(exchange, 404, Map.of("error", "not found: " + path));
            return;
        }
        String runId = path.split("/")[2];
        AgentRun run = runs.findById(runId).orElse(null);
        if (run == null) {
            writeJson(exchange, 404, Map.of("error", "run not found: " + runId));
            return;
        }
        List<Map<String, Object>> planViews = plans.findByRunId(runId).stream()
                .map(this::planView)
                .toList();
        Map<String, Object> runView = new LinkedHashMap<>();
        runView.put("runId", run.runId());
        runView.put("conversationId", run.conversationId());
        runView.put("goal", run.goal());
        runView.put("status", run.status().name());
        runView.put("currentStep", run.currentStep());
        writeJson(exchange, 200, Map.of("run", runView, "plans", planViews));
    }

    /**
     * 调试台右侧面板的数据：窗口（模型实际看到的）、工具调用（代理里实际发生的）、
     * 长期记忆（按 q 检索命中的）。
     */
    private void routeDebug(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.matches("/debug/conversations/[^/]+")
                || !exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            writeJson(exchange, 404, Map.of("error", "not found: " + path));
            return;
        }
        String id = path.split("/")[3];
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String userId = query.getOrDefault("userId", "u1");
        String q = query.getOrDefault("q", "");
        List<Message> history = messages.listMessages(id);
        List<Memory> hits = memories.retrieve(userId, q.isBlank() ? " " : q, 5);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("messages", history.stream()
                .map(m -> Map.<String, Object>of("role", m.role(), "content", m.content()))
                .toList());
        out.put("window", agent.inspectWindow(id));
        out.put("memories", hits.stream()
                .map(m -> Map.<String, Object>of("key", m.memoryKey(), "value", m.memoryValue()))
                .toList());
        out.put("recentToolCalls", agent.recentToolCalls(10).stream()
                .map(r -> Map.<String, Object>of(
                        "tool", r.tool(), "arguments", r.arguments(), "result", r.result()))
                .toList());
        writeJson(exchange, 200, out);
    }

    private Map<String, Object> planView(Plan plan) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("planId", plan.id());
        view.put("steps", plan.steps().stream()
                .map(s -> Map.<String, Object>of(
                        "seq", s.seq(), "description", s.description(), "status", s.status().name()))
                .toList());
        return view;
    }

    private void routeStatic(HttpExchange exchange) throws IOException {
        String resource = exchange.getRequestURI().getPath().equals("/")
                ? "/static/index.html"
                : "/static" + exchange.getRequestURI().getPath();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) {
                writeJson(exchange, 404, Map.of("error", "not found: " + resource));
                return;
            }
            byte[] bytes = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType(resource));
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    // ---------------- 小工具 ----------------

    private static Map<String, Object> readJson(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        if (bytes.length == 0) {
            return Map.of();
        }
        return MAPPER.readValue(bytes, new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
    }

    private static void writeJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return query;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            if (index < 0) {
                continue;
            }
            query.put(
                    URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8));
        }
        return query;
    }

    private static String contentType(String resource) {
        if (resource.endsWith(".html")) {
            return "text/html;charset=UTF-8";
        }
        if (resource.endsWith(".js")) {
            return "text/javascript;charset=UTF-8";
        }
        if (resource.endsWith(".css")) {
            return "text/css;charset=UTF-8";
        }
        return "application/octet-stream";
    }

    /** 仅测试可见：直接访问工具调用记录。 */
    List<TaskTools.ToolCallRecord> toolCallsForTest() {
        return agent.recentToolCalls(10);
    }
}
