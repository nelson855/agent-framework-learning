package com.example.agentlearning.stage03;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Stage 03 CLI Demo —— 确定性演示 Long-running Agent 的完整闭环。
 *
 * <p>用三路 ScriptedLlmClient 分别驱动压缩摘要、交付总结、评估，
 * 因此<b>不依赖真实在线模型</b>也能演示 validator 拒绝 → 重试 → evaluator 通过。
 *
 * <pre>
 * 演示序列：
 *   1. 创建 run + 6 步计划  → 初始 Checkpoint v0
 *   2. step ×2             → Checkpoint v1、v2（第 2 步后触发一次 Compaction）
 *   3. requestInterrupt(2)  → 执行第 3 步前受控中断（run=INTERRUPTED）
 *   4. resumeRun            → 从 Checkpoint 继续，跳过已 DONE 步骤直至完成
 *   5. evaluateRun          → 生成总结 → Validator 拒绝(首条非法) → 重试 → Evaluator 通过
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String dbFile = args.length > 0 ? args[0] : "data/stage03.db";
        Path parent = Paths.get(dbFile).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Database db = new Database("jdbc:sqlite:" + dbFile)) {
            RunService service = RunService.create(db, summarizerScript(), reportScript(), evaluatorScript());
            runDemo(service);
        }
    }

    static void runDemo(RunService service) {
        String goal = "根据项目规范制定一个 6 步开发计划，每完成一步记录结果；如果中断则下次继续；最后生成符合规范的 JSON 总结。";

        String runId = service.createRun(goal);
        System.out.println("============================================================");

        System.out.println("\n--- 执行第 1~2 步 ---");
        StepOutcome o1 = service.stepRun(runId);
        System.out.println("outcome: " + o1.status() + " step=" + o1.stepId());
        StepOutcome o2 = service.stepRun(runId);
        System.out.println("outcome: " + o2.status() + " step=" + o2.stepId());

        System.out.println("\n--- 请求在第 3 步前中断 ---");
        service.requestInterrupt(2);
        StepOutcome o3 = service.stepRun(runId);
        System.out.println("outcome: " + o3.status() + " step=" + o3.stepId()
                + " | " + o3.message());
        System.out.println("run status: " + service.getRun(runId).status());

        System.out.println("\n--- Resume：从 Checkpoint 继续（应跳过 S1/S2） ---");
        StepOutcome resume = service.resumeRun(runId);
        System.out.println("resume done: " + resume.status() + " | " + resume.message());
        System.out.println("run status: " + service.getRun(runId).status());

        System.out.println("\n--- Checkpoint 时间线 ---");
        for (VersionedCheckpoint cp : service.checkpoints(runId)) {
            System.out.println("  v" + cp.version() + " savedAt=" + cp.savedAt()
                    + " nextStep=" + cp.currentStep());
        }

        System.out.println("\n--- Context 快照（片段） ---");
        for (ContextSnapshotRepository.SimpleSnapshot s : service.contextSnapshots(runId)) {
            System.out.println("  step#" + s.stepIndex() + ": " + firstLine(s.context()));
        }

        System.out.println("\n--- 压缩摘要 ---");
        List<String> compacts = service.compactionSummaries(runId);
        if (compacts.isEmpty()) {
            System.out.println("  (未触发)");
        } else {
            compacts.forEach(c -> System.out.println("  " + c));
        }

        System.out.println("\n--- 校验 / 评估 ---");
        List<String> evalLog = service.evaluateRun(runId);
        evalLog.forEach(l -> System.out.println("  " + l));

        System.out.println("\n--- 评估记录 ---");
        for (EvaluationRepository.EvaluationEntry e : service.evaluations(runId)) {
            System.out.println("  iter" + e.iteration() + " validator=" + (e.validatorPass() ? "pass" : "reject")
                    + " evaluator=" + (e.evaluatorPass() ? "pass(" + e.evaluatorScore() + ")" : "reject"));
        }
        System.out.println("\n============================================================");
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }

    static LlmClient summarizerScript() {
        return ScriptedLlmClient.of(
                "{\"goal\":\"制定并执行 6 步开发计划\",\"completed\":[\"收集规范\",\"设计数据模型\"],"
                        + "\"importantFacts\":[\"SQLite TEXT UUID 主键\"],\"decisions\":[\"分层架构\"],"
                        + "\"pendingActions\":[\"生成 JSON 总结\"]}");
    }

    static LlmClient reportScript() {
        // 首条非法 JSON（演示 Validator 拒绝），第二条合法（经反馈后重试）
        return ScriptedLlmClient.of(
                "这不该是一个 JSON 总结，缺少必要字段",
                """
                {"projectName":"dev-plan",
                 "planSteps":6,
                 "completedSteps":["S1","S2","S3","S4","S5","S6"],
                 "summary":"按规范完成 6 步开发计划，采用 SQLite 存储与分层架构，并通过校验评估。",
                 "recommendations":["补充更多测试","补充演练文档"]}""");
    }

    static LlmClient evaluatorScript() {
        // 首条评估：先拒绝（反馈驱动优化），第二条评估：通过
        return ScriptedLlmClient.of(
                "{\"pass\":false,\"score\":2,\"issues\":[\"summary 缺少关键决策细节\",\"recommendations 不够具体\"]}",
                "{\"pass\":true,\"score\":4,\"issues\":[]}");
    }
}