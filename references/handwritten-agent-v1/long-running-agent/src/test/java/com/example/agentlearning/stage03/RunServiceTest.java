package com.example.agentlearning.stage03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 集成测试：用三路 ScriptedLlmClient 驱动完整闭环，验证 Long-running 机制。
 *
 * <p>覆盖验收 1~7：6 步任务、中途 Checkpoint、受控中断、Resume 从正确步骤继续、
 * Observable Compaction、Validator 制造失败、Evaluator 反馈优化。
 */
class RunServiceTest {

    private final String goal = "根据项目规范制定一个 6 步开发计划，每完成一步记录结果；如果中断则下次继续；最后生成符合规范的 JSON 总结。";

    private RunService service(Database db) {
        return RunService.create(db,
                ScriptedLlmClient.of(
                        "{\"goal\":\"目标\",\"completed\":[\"c1\"],\"importantFacts\":[\"f\"],"
                                + "\"decisions\":[\"d\"],\"pendingActions\":[\"p\"]}"),
                ScriptedLlmClient.of(
                        "非法 JSON 缺少字段",
                        "{\"projectName\":\"p\",\"planSteps\":6,\"completedSteps\":[\"S1\"],"
                                + "\"summary\":\"总结\",\"recommendations\":[\"A\"]}"),
                ScriptedLlmClient.of(
                        "{\"pass\":false,\"score\":2,\"issues\":[\"缺细节\"]}",
                        "{\"pass\":true,\"score\":4,\"issues\":[]}"));
    }

    @Test
    void fullFlowInterruptResumeAndEvaluate() {
        try (Database db = new Database("jdbc:sqlite::memory:")) {
            RunService service = service(db);
            String runId = service.createRun(goal);

            assertEquals(RunStatus.RUNNING, service.getRun(runId).status());

            // 前 2 步
            StepOutcome o1 = service.stepRun(runId);
            StepOutcome o2 = service.stepRun(runId);
            assertEquals("S2", o2.stepId());
            assertTrue(service.getState(runId).stepResults().size() >= 2);

            // 受控中断：请求在第 3 步前打断
            service.requestInterrupt(2);
            StepOutcome interrupted = service.stepRun(runId);
            assertEquals(RunStatus.INTERRUPTED, interrupted.status());
            assertEquals("S3", interrupted.stepId());
            assertEquals(RunStatus.INTERRUPTED, service.getRun(runId).status());

            // Resume：从 Checkpoint 继续（S1/S2 已 DONE，不重跑），直至完成
            StepOutcome resume = service.resumeRun(runId);
            assertEquals(RunStatus.COMPLETED, resume.status());
            assertEquals(RunStatus.COMPLETED, service.getRun(runId).status());
            assertEquals("S6", resume.stepId()); // 最后完成的是第 6 步
            assertTrue(service.getState(runId).isComplete());

            // 全部 6 步都被执行
            long done = service.getState(runId).plan().stream()
                    .filter(s -> s.status() == StepStatus.DONE).count();
            assertEquals(6, done);

            // Checkpoint 版本化：每个推进都有新版本
            List<VersionedCheckpoint> cps = service.checkpoints(runId);
            assertTrue(cps.size() >= 6, "应该保存多个版本化的 checkpoint");
            assertEquals(cps.get(0).version(), 1);

            // Observable Compaction：确定性地应触发一次
            // （每步结果较长，第 2 步推进后超过默认阈值 100 字符）
            assertTrue(service.getState(runId).compacted());
            assertTrue(service.compactionSummaries(runId).size() >= 1);

            // 校验/评估：首条非法被 Validator 拒绝，经反馈重试后 Evaluator 通过
            List<String> evalLog = service.evaluateRun(runId);
            assertTrue(evalLog.stream().anyMatch(l -> l.contains("VALIDATOR REJECTED")));
            assertTrue(evalLog.stream().anyMatch(l -> l.contains("EVALUATOR PASSED")));
        }
    }

    @Test
    void createRunBuildsPlanAndInitialCheckpoint() {
        try (Database db = new Database("jdbc:sqlite::memory:")) {
            RunService service = service(db);
            String runId = service.createRun(goal);
            AgentState state = service.getState(runId);
            assertEquals(6, state.plan().size());
            assertEquals(1, service.checkpoints(runId).size()); // v0 之后首版
        }
    }
}