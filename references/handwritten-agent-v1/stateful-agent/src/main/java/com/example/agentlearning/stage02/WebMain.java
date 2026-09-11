package com.example.agentlearning.stage02;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 02 Web 入口：启动 JDK HttpServer 和 Agent 核心服务。
 *
 * <p>Web 层只做 HTTP/JSON 转换，不实现 Agent 逻辑。
 * 如果把 Web 层删掉，核心 Agent 仍可通过 CLI 或测试运行。
 */
public final class WebMain {

    private static final int DEFAULT_PORT = 8080;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebMain() {
    }

    public static void main(String[] args) throws IOException {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        Database db = new Database("jdbc:sqlite:data/stage02.db");
        LlmClient llm = OpenAiCompatibleLlmClient.isConfigured()
                ? OpenAiCompatibleLlmClient.fromConfig()
                : offlineWebLlm();
        if (!OpenAiCompatibleLlmClient.isConfigured()) {
            System.out.println("[警告] 未配置真实 LLM，使用离线脚本模型（仅演示页面功能）。");
            System.out.println("       在仓库根目录放 .env 可接入真实模型。");
        }
        AppComponents app = AppComponents.build(llm, db);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(app, MAPPER));
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Stage 02 启动: http://localhost:" + port);
        System.out.println("页面左侧: Conversation History（对话历史）");
        System.out.println("页面右侧上: Current State（Agent 状态） / Plan（计划）");
        System.out.println("页面右侧下: Retrieved Memory（检索到的长期记忆）");
    }

    private static FakeLlmClient offlineWebLlm() {
        return new FakeLlmClient(
                "{\"shouldRemember\":false}",
                "{\"goal\":\"离线演示\",\"steps\":[{\"id\":\"S1\",\"description\":\"第一步\"},{\"id\":\"S2\",\"description\":\"第二步\"},{\"id\":\"S3\",\"description\":\"第三步\"}]}",
                "{\"type\":\"final\",\"answer\":\"（离线模式）观察页面的 State / Plan / Memory 区块。\"}",
                "{\"type\":\"final\",\"answer\":\"（第二步完成）\"}",
                "{\"type\":\"final\",\"answer\":\"（全部完成）\"}");
    }

    // ---------------- Handlers ----------------

    private static final class ApiHandler implements HttpHandler {
        private final AppComponents app;
        private final ObjectMapper mapper;

        ApiHandler(AppComponents app, ObjectMapper mapper) {
            this.app = app;
            this.mapper = mapper;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                handleInternal(exchange);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", e.getMessage()));
            }
        }

        private void handleInternal(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("POST".equals(method) && "/api/conversations".equals(path)) {
                Map<String, Object> body = readJson(exchange.getRequestBody());
                String title = String.valueOf(body.getOrDefault("title", "新会话"));
                Conversation c = app.conversations.createConversation(title);
                sendJson(exchange, 200, Map.of("id", c.id(), "title", c.title(), "createdAt", c.createdAt()));
                return;
            }

            if ("GET".equals(method) && "/api/conversations".equals(path)) {
                List<Conversation> list = app.conversations.listConversations();
                sendJson(exchange, 200, list);
                return;
            }

            if ("POST".equals(method) && "/api/chat".equals(path)) {
                Map<String, Object> body = readJson(exchange.getRequestBody());
                String conversationId = (String) body.get("conversationId");
                String message = (String) body.get("message");
                if (conversationId == null || message == null || message.isBlank()) {
                    sendJson(exchange, 400, Map.of("error", "需要 conversationId 和 message"));
                    return;
                }
                ChatResult result = app.agent.chat(conversationId, message);
                Map<String, Object> response = chatResultToMap(result);
                sendJson(exchange, 200, response);
                return;
            }

            if (matchPath(path, "/api/conversations/", "/state") != null && "GET".equals(method)) {
                String conversationId = matchPath(path, "/api/conversations/", "/state");
                java.util.Optional<AgentRun> run = app.runs.findLatestByConversation(conversationId);
                Map<String, Object> response = new LinkedHashMap<>();
                if (run.isPresent()) {
                    AgentRun r = run.get();
                    response.put("runId", r.runId());
                    response.put("goal", r.goal());
                    response.put("status", r.status().name());
                    response.put("currentStep", r.currentStep());
                    response.put("startedAt", r.startedAt());
                    app.planRepo.findLatestByRun(r.runId()).ifPresent(plan -> {
                        response.put("planGoal", plan.goal());
                        response.put("steps", plan.steps().stream().map(s -> Map.of(
                                "id", s.id(),
                                "description", s.description(),
                                "status", s.status().name(),
                                "failureReason", s.failureReason() != null ? s.failureReason() : ""
                        )).toList());
                    });
                }
                List<StoredMessage> msgs = app.messages.findByConversation(conversationId);
                response.put("messages", msgs.stream().map(m -> Map.of(
                        "id", m.id(),
                        "role", m.role(),
                        "content", m.content(),
                        "createdAt", m.createdAt()
                )).toList());
                boolean hasConversation = app.conversations.findConversationById(conversationId).isPresent();
                response.put("exists", hasConversation);
                sendJson(exchange, 200, response);
                return;
            }

            if (matchPath(path, "/api/conversations/", "/memories") != null && "GET".equals(method)) {
                List<Memory> mems = app.memories.findAll();
                sendJson(exchange, 200, mems.stream().map(m -> Map.of(
                        "id", m.id(),
                        "type", m.type(),
                        "content", m.content(),
                        "importance", m.importance()
                )).toList());
                return;
            }

            sendJson(exchange, 404, Map.of("error", "未找到: " + method + " " + path));
        }

        private Map<String, Object> chatResultToMap(ChatResult r) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("conversationId", r.conversationId());
            map.put("runId", r.runId());
            map.put("answer", r.answer());
            map.put("status", r.status().name());
            map.put("currentStep", r.currentStep());
            if (r.plan() != null) {
                map.put("planGoal", r.plan().goal());
                map.put("steps", r.plan().steps().stream().map(s -> Map.of(
                        "id", s.id(),
                        "description", s.description(),
                        "status", s.status().name(),
                        "failureReason", s.failureReason() != null ? s.failureReason() : ""
                )).toList());
            }
            map.put("retrievedMemories", r.retrievedMemories().stream().map(m -> Map.of(
                    "id", m.id(),
                    "type", m.type(),
                    "content", m.content()
            )).toList());
            map.put("memorySaved", r.memorySaved());
            map.put("memoryContent", r.memoryContent() != null ? r.memoryContent() : "");
            map.put("messages", r.messages().stream().map(m -> Map.of(
                    "id", m.id(),
                    "role", m.role(),
                    "content", m.content(),
                    "createdAt", m.createdAt()
            )).toList());
            return map;
        }

        private String matchPath(String path, String prefix, String suffix) {
            if (path.startsWith(prefix) && path.endsWith(suffix)) {
                String middle = path.substring(prefix.length());
                middle = middle.substring(0, middle.length() - suffix.length());
                return middle.isEmpty() ? null : middle;
            }
            return null;
        }
    }

    private static final class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/")) {
                path = "/web/index.html";
            } else if (!path.startsWith("/web/")) {
                path = "/web" + path;
            }

            String resource = path.startsWith("/") ? path.substring(1) : path;
            InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
            if (in == null) {
                String body = "404 Not Found: " + path;
                exchange.sendResponseHeaders(404, body.length());
                exchange.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().close();
                return;
            }

            String contentType = contentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            byte[] data = readAll(in);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.getResponseBody().close();
        }

        private byte[] readAll(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }

        private String contentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            return "application/octet-stream";
        }
    }

    // ---------------- JSON 工具 ----------------

    private static void sendJson(HttpExchange exchange, int code, Object value) throws IOException {
        byte[] data = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(InputStream body) throws IOException {
        return MAPPER.readValue(body, LinkedHashMap.class);
    }
}