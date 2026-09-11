package com.example.agentlearning.stage04;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Stage 04 CLI 演示：用确定性假模型跑一次复杂任务，观察 HITL 审批流 + Trace 时间线 + Evaluation。
 *
 * <p>同一套 {@link HarnessService} 也可被 {@link WebMain} 复用（Web 层只是另一入口）。
 * 配置根目录 {@code .env} 时也可接入真实模型（可选手工实验，不作 Maven 测试前置）。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Main.ensureDataDir();
        Database db = new Database(HarnessService.DB_URL_DEFAULT);
        try {
            // 默认确定性假模型；真实模型是手工实验，需显式 -DuseRealModel=true
            boolean real = Boolean.getBoolean("useRealModel");
            if (real && !OpenAiCompatibleLlmClient.isConfigured()) {
                System.out.println("[警告] 未检测到 LLM 配置（LLM_BASE_URL/API_KEY/MODEL），回落假模型");
                real = false;
            }
            HarnessService svc = buildService(db, real);
            System.out.println("==== stage04 Mini Agent Harness (CLI) ====");

            String goal = "阅读本地规范，创建两个子任务，按规范删除一个高风险任务，再交给 review-worker 核对，最后总结并评估";
            System.out.println("\n目标: " + goal);

            AgentRunner.RunResult result = svc.run(goal);
            System.out.println("\nRUN 状态: " + result.status());

            if (result.status() == RunStatus.WAITING_APPROVAL) {
                ApprovalService.ApprovalRequest req = svc.approval.findById(result.approvalId()).orElseThrow();
                System.out.println("需要人工审批 #" + req.id() + ": 风险=" + req.risk() + " 理由=" + req.reason());
                System.out.print("批准删除? (y=批准 / n=拒绝): ");
                String line = new Scanner(System.in).nextLine().trim();
                boolean approved = "y".equalsIgnoreCase(line);
                result = svc.resumeAfterApproval(req.id(), approved);
                System.out.println("审批" + (approved ? "批准" : "拒绝") + " → RUN 状态: " + result.status()
                        + (result.answer() != null ? "\n最终回答: " + result.answer() : ""));
            }

            printTrace(svc, result);
            printEval(svc, result);
        } finally {
            db.close();
        }
    }

    /** 组装 Harness：真实模型可用则用，否则用确定性脚本模型。 */
    public static HarnessService buildService(Database db, boolean useRealModel) {
        if (useRealModel) {
            OpenAiCompatibleLlmClient real = OpenAiCompatibleLlmClient.fromConfig();
            Map<String, LlmClient> workers = Map.of(
                    "research-worker", (LlmClient) real,
                    "review-worker", (LlmClient) real);
            return new HarnessService(db, real, workers, List.of("research-worker", "review-worker"));
        }
        Map<String, LlmClient> workers = Map.of(
                "research-worker", (LlmClient) new FunctionLlmClient(Main::workerResearchResponder),
                "review-worker", (LlmClient) new FunctionLlmClient(Main::workerReviewResponder));
        return new HarnessService(db, new FunctionLlmClient(Main::demoResponder), workers,
                List.of("research-worker", "review-worker"));
    }

    /** 确保 SQLite 数据目录存在（sqlite-jdbc 不会自动建目录）。 */
    public static void ensureDataDir() {
        java.nio.file.Path dir = java.nio.file.Path.of("data");
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("创建 data 目录失败", e);
        }
    }

    private static void printTrace(HarnessService svc, AgentRunner.RunResult result) {
        String runId = result.runId();
        List<TraceEvent> events = svc.trace.eventsFor(runId);
        System.out.println("\n—— Trace 时间线（共 " + events.size() + " 条）——");
        for (TraceEvent e : events) {
            String who = e.workerId() == null ? "ORCH" : "W[" + e.workerId() + "]";
            System.out.printf("  %-4s %-22s %s%n", who, e.type(), e.message());
        }
        System.out.println("—— 工具调用 ——");
        for (TraceService.ToolEvent t : svc.trace.toolEventsFor(runId)) {
            System.out.printf("  %-10s %-4s %s%n", t.tool(), t.success() ? "OK " : "ERR", t.args());
        }
    }

    private static void printEval(HarnessService svc, AgentRunner.RunResult result) {
        Evaluator.EvaluationResult eval = svc.evaluator.evaluate(result.runId());
        System.out.println("\n—— Evaluation ——");
        System.out.printf("  pass=%s steps=%d toolCalls=%d toolErrors=%d approvals=%d notes=%s%n",
                eval.pass(), eval.steps(), eval.toolCalls(), eval.toolErrors(),
                eval.approvalCount(), eval.notes());
    }

    // ---------- 确定性脚本模型 ----------

    /** Orchestrator 模型：一段固定决策序列，直到触发审批、批准后 final。 */
    static String demoResponder(List<Message> messages) {
        String[] script = {
                toolCall("getDoc", Map.of("title", "高风险操作规范"), "先读高风险规范"),
                toolCall("remember", Map.of("key", "删除", "value", "删除任务需人工批准"), "把规范记入记忆"),
                toolCall("createTask", Map.of("title", "编写 Harness 组件文档"), "创建子任务1"),
                toolCall("createTask", Map.of("title", "补充验收测试"), "创建子任务2"),
                toolCall("delegateTo", Map.of("worker", "review-worker", "task", "核对删除策略是否需要人工批准"), "交接核对"),
                toolCall("deleteTask", Map.of("taskId", "T1"), "按规范删除高风险任务 T1"),
                finalJson("已完成：读取高风险规范、创建两个子任务、经 review-worker 核对删除策略、删除任务 T1（已批准）。")
        };
        return script[Math.min(observationCount(messages), script.length - 1)];
    }

    /** research-worker 模型。 */
    static String workerResearchResponder(List<Message> messages) {
        String[] script = {
                toolCall("getDoc", Map.of("title", "任务管理规范"), "研究任务管理规范"),
                finalJson("调研完成：任务默认 OPEN，可创建与删除（删除需审批）。")
        };
        return script[Math.min(observationCount(messages), script.length - 1)];
    }

    /** review-worker 模型。 */
    static String workerReviewResponder(List<Message> messages) {
        String[] script = {
                toolCall("getDoc", Map.of("title", "高风险操作规范"), "核对高风险规范"),
                finalJson("核对完成：确认 deleteTask 必须等待人工审批。")
        };
        return script[Math.min(observationCount(messages), script.length - 1)];
    }

    static int observationCount(List<Message> messages) {
        return (int) messages.stream()
                .filter(m -> m.role() == Role.USER && m.content().startsWith("[observation]"))
                .count();
    }

    static String toolCall(String tool, Map<String, Object> args, String summary) {
        try {
            String argsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(args);
            return "{\"type\":\"tool_call\",\"tool\":\"" + tool + "\",\"arguments\":" + argsJson
                    + ",\"decisionSummary\":\"" + summary + "\"}";
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static String finalJson(String answer) {
        return "{\"type\":\"final\",\"answer\":\"" + answer + "\"}";
    }
}