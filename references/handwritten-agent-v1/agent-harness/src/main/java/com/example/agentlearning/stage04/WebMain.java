package com.example.agentlearning.stage04;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 04 Web 入口：Mini Agent Harness 控制台。
 *
 * <p>Web 层只做 HTTP/JSON 转换，核心逻辑全部在 {@link HarnessService} / {@link AgentRunner}。
 * 删除 Web 层，同一 Service 仍可被 Main CLI 或测试驱动。
 *
 * <p>端点：
 * <pre>
 * POST /api/runs {goal}              创建并运行到「等待审批」或「完成」
 * GET  /api/runs/{id}/overview       运行概览：状态/计划/记忆知识/Metrics
 * GET  /api/runs/{id}/tools          工具调用明细
 * GET  /api/runs/{id}/trace          Trace 时间线
 * GET  /api/runs/{id}/workers        Worker 明细（Multi-Agent）
 * GET  /api/approvals                审批队列
 * POST /api/approvals/{id}/approve   批准 → 从 Checkpoint 恢复执行
 * POST /api/approvals/{id}/reject    拒绝 → 从 Checkpoint 恢复（不执行）
 * POST /api/demo                     跑内定综合演示任务
 * </pre>
 */
public final class WebMain {

    private static final int DEFAULT_PORT = 8080;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEMO_GOAL =
            "阅读本地规范，创建两个子任务，按规范删除一个高风险任务，再交给 review-worker 核对，最后总结并评估";

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

        Main.ensureDataDir();
        Database db = new Database(HarnessService.DB_URL_DEFAULT);
        // 默认确定性假模型；真实模型是手工实验，需显式 -DuseRealModel=true
        boolean real = Boolean.getBoolean("useRealModel") && OpenAiCompatibleLlmClient.isConfigured();
        HarnessService svc = Main.buildService(db, real);
        if (!real) {
            System.out.println("[警告] 未配置真实 LLM，使用离线脚本模型（仅演示页面功能）。");
            System.out.println("       在仓库根目录放 .env 可接入真实模型。");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(svc));
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Stage 04 启动: http://localhost:" + port);
        System.out.println("面板观察：Run&Plan / Tool Events / Memory&Knowledge / Worker&Handoff");
        System.out.println("         / Approval Queue / Trace Timeline / Metrics");
    }

    // ---------------- Handlers ----------------

    private static final class ApiHandler implements HttpHandler {
        private final HarnessService svc;

        ApiHandler(HarnessService svc) {
            this.svc = svc;
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

            if ("POST".equals(method) && "/api/runs".equals(path)) {
                Map<String, Object> body = readJson(exchange);
                String goal = String.valueOf(body.getOrDefault("goal", DEMO_GOAL));
                AgentRunner.RunResult result = svc.run(goal);
                sendJson(exchange, 200, overview(result.runId()));
                return;
            }

            if ("POST".equals(method) && "/api/demo".equals(path)) {
                AgentRunner.RunResult result = svc.run(DEMO_GOAL);
                sendJson(exchange, 200, overview(result.runId()));
                return;
            }

            if ("GET".equals(method) && "/api/approvals".equals(path)) {
                sendJson(exchange, 200, approvals());
                return;
            }

            String runId = runIdOf(path, "/api/runs/");
            if (runId != null) {
                if ("GET".equals(method) && path.endsWith("/overview")) {
                    sendJson(exchange, 200, overview(runId));
                    return;
                }
                if ("GET".equals(method) && path.endsWith("/tools")) {
                    sendJson(exchange, 200, tools(runId));
                    return;
                }
                if ("GET".equals(method) && path.endsWith("/trace")) {
                    sendJson(exchange, 200, trace(runId));
                    return;
                }
                if ("GET".equals(method) && path.endsWith("/workers")) {
                    sendJson(exchange, 200, workers(runId));
                    return;
                }
            }

            String approvalId = idOf(path, "/api/approvals/");
            if (approvalId != null) {
                int id = Integer.parseInt(approvalId);
                if ("POST".equals(method) && path.endsWith("/approve")) {
                    String runId2 = svc.approval.findById(id).orElseThrow().runId();
                    svc.resumeAfterApproval(id, true);
                    sendJson(exchange, 200, overview(runId2));
                    return;
                }
                if ("POST".equals(method) && path.endsWith("/reject")) {
                    String runId2 = svc.approval.findById(id).orElseThrow().runId();
                    svc.resumeAfterApproval(id, false);
                    sendJson(exchange, 200, overview(runId2));
                    return;
                }
            }

            sendJson(exchange, 404, Map.of("error", "未找到: " + method + " " + path));
        }

        private Map<String, Object> overview(String runId) {
            Map<String, Object> resp = new LinkedHashMap<>();
            RunStore.RunRow run = svc.runStore.findByRunId(runId);
            if (run == null) {
                return Map.of("runId", runId, "status", "UNKNOWN");
            }
            resp.put("runId", run.runId());
            resp.put("goal", run.goal());
            resp.put("status", run.status().name());
            resp.put("currentStep", run.currentStep());
            resp.put("plan", svc.planOf(runId).stream().map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.id());
                m.put("description", p.description());
                m.put("status", p.status().name());
                return m;
            }).toList());

            resp.put("memory", svc.memory.all().stream().map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", e.key());
                m.put("value", e.value());
                return m;
            }).toList());
            resp.put("knowledge", svc.knowledge.allTitles());

            Evaluator.EvaluationResult eval = svc.evaluator.evaluate(runId);
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("steps", eval.steps());
            metrics.put("toolCalls", eval.toolCalls());
            metrics.put("toolErrors", eval.toolErrors());
            metrics.put("approvals", eval.approvalCount());
            metrics.put("pass", eval.pass());
            metrics.put("notes", eval.notes());
            resp.put("metrics", metrics);
            resp.put("workers", svc.workerNames());
            return resp;
        }

        private Map<String, Object> tools(String runId) {
            return Map.of("tools", svc.trace.toolEventsFor(runId).stream().map(t -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tool", t.tool());
                m.put("workerId", t.workerId());
                m.put("args", t.args());
                m.put("success", t.success());
                m.put("result", t.result());
                m.put("elapsedMs", t.elapsedMs());
                return m;
            }).toList());
        }

        private Map<String, Object> trace(String runId) {
            return Map.of("events", svc.trace.eventsFor(runId).stream().map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("workerId", e.workerId());
                m.put("type", e.type().name());
                m.put("message", e.message());
                m.put("at", e.at());
                return m;
            }).toList());
        }

        private Map<String, Object> workers(String runId) {
            List<Map<String, Object>> list = svc.workerNames().stream().map(name -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", name);
                m.put("events", svc.trace.eventsFor(runId).stream()
                        .filter(e -> name.equals(e.workerId()))
                        .map(TraceEvent::message).toList());
                return m;
            }).toList();
            return Map.of("workers", list);
        }

        private Map<String, Object> approvals() {
            return Map.of("approvals", svc.approval.all().stream().map(a -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", a.id());
                m.put("runId", a.runId());
                m.put("toolCall", a.toolCallJson());
                m.put("reason", a.reason());
                m.put("risk", a.risk());
                m.put("status", a.status().name());
                return m;
            }).toList());
        }

        private static String runIdOf(String path, String prefix) {
            return idOf(path, prefix);
        }

        private static String idOf(String path, String prefix) {
            if (!path.startsWith(prefix)) {
                return null;
            }
            String rest = path.substring(prefix.length());
            int slash = rest.indexOf('/');
            return slash < 0 ? rest : rest.substring(0, slash);
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
            exchange.getResponseHeaders().set("Content-Type", contentType(path));
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
    private static Map<String, Object> readJson(HttpExchange exchange) throws IOException {
        return MAPPER.readValue(exchange.getRequestBody(), LinkedHashMap.class);
    }
}