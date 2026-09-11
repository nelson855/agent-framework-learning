package com.example.agentlearning.stage04;

import java.util.List;
import java.util.Map;

/**
 * 测试共享工具：构造内存态 HarnessService 与脚本化假模型。
 * 复用 {@link Main} 的 toolCall / finalJson / observationCount 助手。
 */
final class TestSupport {

    private TestSupport() {
    }

    /** 用一个内存数据库 + 脚本化 Orchestrator 组装 Harness。Worker 默认立即 final。 */
    static HarnessService service(FunctionLlmClient orchestrator) {
        Map<String, LlmClient> workers = Map.of(
                "research-worker", (LlmClient) script(Main.finalJson("worker done")),
                "review-worker", (LlmClient) script(Main.finalJson("worker done")));
        return new HarnessService(new Database("jdbc:sqlite::memory:"),
                orchestrator, workers, List.of("research-worker", "review-worker"));
    }

    /** 给一个指定 Worker 专属模型的 Harness。 */
    static HarnessService service(FunctionLlmClient orchestrator, String workerName, LlmClient workerModel) {
        Map<String, LlmClient> workers = Map.of(workerName, workerModel);
        return new HarnessService(new Database("jdbc:sqlite::memory:"),
                orchestrator, workers, List.of(workerName));
    }

    /** 脚本化模型：按 observation 数量返回 steps[i]，越界用最后一步。 */
    static FunctionLlmClient script(String... steps) {
        return new FunctionLlmClient(messages ->
                steps[Math.min(Main.observationCount(messages), steps.length - 1)]);
    }

    static String getDoc() {
        return Main.toolCall("getDoc", Map.of("title", "高风险操作规范"), "读规范");
    }

    static String createTask(String title) {
        return Main.toolCall("createTask", Map.of("title", title), "建任务");
    }

    static String deleteTask(String id) {
        return Main.toolCall("deleteTask", Map.of("taskId", id), "删任务");
    }

    static String delegateTo(String worker, String task) {
        return Main.toolCall("delegateTo", Map.of("worker", worker, "task", task), "交接");
    }
}