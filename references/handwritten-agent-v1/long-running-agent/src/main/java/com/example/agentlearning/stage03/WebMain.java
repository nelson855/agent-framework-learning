package com.example.agentlearning.stage03;

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
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 03 Web 入口：Long-running Agent 调试台。
 *
 * <p>Web 层只做 HTTP/JSON 转换，核心逻辑全部在 {@link RunService}。
 * 如果删掉 Web 层，核心 Agent 仍可通过 Main CLI 或测试运行。
 *
 * <p>动作：创建 run、逐步执行、模拟中断、Resume、评估；
 * 观察：Run Overview、Plan、Context Inspector、Checkpoint Timeline、Validation/Evaluator。
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

        Database db = new Database("jdbc:sqlite:data/stage03.db");
        RunService service = buildService(db);
        if (!OpenAiCompatibleLlmClient.isConfigured()) {
            System.out.println("[警告] 未配置真实 LLM，使用离线脚本模型（仅演示页面功能）。");
            System.out.println("       在仓库根目录放 .env 可接入真实模型。");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(service));
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Stage 03 启动: http://localhost:" + port);
        System.out.println("流程：输入目标 → Start → (Step×N 或 Simulate Interruption → Resume) → Evaluate");
        System.out.println("右侧观察：Run / Plan / Context / Checkpoint / Evaluation");
    }

    private static RunService buildService(Database db) {
        if (OpenAiCompatibleLlmClient.isConfigured()) {
            OpenAiCompatibleLlmClient real = OpenAiCompatibleLlmClient.fromConfig();
            // 三路共用真实模型：压缩摘要 / 交付总结 / 评估
            return RunService.create(db, real, real, real);
        }
        return RunService.create(db,
                Main.summarizerScript(), Main.reportScript(), Main.evaluatorScript());
    }

    // ---------------- Handlers ----------------

    private static final class ApiHandler implements HttpHandler {
        private final RunService service;

        ApiHandler(RunService service) {
            this.service = service;
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
                String goal = String.valueOf(body.getOrDefault("goal", "根据项目规范制定并执行 6 步开发计划"));
                String runId = service.createRun(goal);
                sendJson(exchange, 200, overview(runId));
                return;
            }

            String runId = runIdOf(path, "/api/runs/");
            if (runId == null) {
                sendJson(exchange, 404, Map.of("error", "未找到: " + path));
                return;
            }

            if ("GET".equals(method) && path.endsWith("/overview")) {
                sendJson(exchange, 200, overview(runId));
                return;
            }

            if ("POST".equals(method) && path.endsWith("/step")) {
                StepOutcome o = service.stepRun(runId);
                sendJson(exchange, 200, outcomeToMap(o));
                return;
            }

            if ("POST".equals(method) && path.endsWith("/interrupt")) {
                AgentState state = service.getState(runId);
                int target = (state != null && state.nextPendingStepIndex() >= 0)
                        ? state.nextPendingStepIndex()
                        : 0;
                service.requestInterrupt(target);
                sendJson(exchange, 200, Map.of("ok", true, "interruptAtStep", target + 1));
                return;
            }

            if ("POST".equals(method) && path.endsWith("/resume")) {
                StepOutcome o = service.resumeRun(runId);
                sendJson(exchange, 200, outcomeToMap(o));
                return;
            }

            if ("POST".equals(method) && path.endsWith("/evaluate")) {
                List<String> log = service.evaluateRun(runId);
                sendJson(exchange, 200, Map.of("log", log));
                return;
            }

            if ("GET".equals(method) && path.endsWith("/checkpoints")) {
                List<Map<String, Object>> list = service.checkpoints(runId).stream().map(cp -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("version", cp.version());
                    m.put("runId", cp.runId());
                    m.put("savedAt", cp.savedAt());
                    m.put("currentStep", cp.currentStep());
                    m.put("compacted", cp.state().compacted());
                    return m;
                }).toList();
                sendJson(exchange, 200, list);
                return;
            }

            if ("GET".equals(method) && path.endsWith("/context")) {
                sendJson(exchange, 200, context(runId));
                return;
            }

            if ("GET".equals(method) && path.endsWith("/evaluations")) {
                List<Map<String, Object>> list = service.evaluations(runId).stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("iteration", e.iteration());
                    m.put("validatorPass", e.validatorPass());
                    m.put("validatorErrors", e.validatorErrors());
                    m.put("evaluatorPass", e.evaluatorPass());
                    m.put("evaluatorScore", e.evaluatorScore());
                    m.put("evaluatorIssues", e.evaluatorIssues());
                    m.put("reportText", e.reportText());
                    return m;
                }).toList();
                sendJson(exchange, 200, list);
                return;
            }

            sendJson(exchange, 404, Map.of("error", "未找到: " + method + " " + path));
        }

        private Map<String, Object> overview(String runId) {
            Map<String, Object> resp = new LinkedHashMap<>();
            Run run = service.getRun(runId);
            AgentState state = service.getState(runId);
            resp.put("runId", run != null ? run.runId() : runId);
            resp.put("status", run != null ? run.status().name() : "UNKNOWN");
            resp.put("currentStep", run != null ? run.currentStep() : 0);

            if (state != null && state.plan() != null) {
                List<Map<String, Object>> steps = new java.util.ArrayList<>();
                for (PlanStep s : state.plan()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.id());
                    m.put("description", s.description());
                    m.put("status", s.status().name());
                    m.put("tool", s.tool());
                    m.put("result", s.result() != null ? s.result() : "");
                    steps.add(m);
                }
                resp.put("plan", steps);
                resp.put("stepResults", state.stepResults());
                resp.put("compacted", state.compacted());
            }
            return resp;
        }

        private Map<String, Object> context(String runId) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ragDocs", service.ragDocs(runId).stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", d.title());
                m.put("content", d.content());
                return m;
            }).toList());
            resp.put("memories", service.memoriesSnapshot(runId).stream().map(mem -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", mem.type());
                m.put("content", mem.content());
                return m;
            }).toList());
            resp.put("compactionSummaries", service.compactionSummaries(runId));
            resp.put("snapshots", service.contextSnapshots(runId).stream().map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("stepIndex", s.stepIndex());
                m.put("context", s.context());
                m.put("createdAt", s.createdAt());
                return m;
            }).toList());
            return resp;
        }

        private Map<String, Object> outcomeToMap(StepOutcome o) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", o.status().name());
            m.put("stepId", o.stepId());
            m.put("stepIndex", o.stepIndex());
            m.put("message", o.message());
            return m;
        }

        private static String runIdOf(String path, String prefix) {
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