package com.example.agentlearning.stage04;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AgentRunner 核心不变量：高风险 deleteTask 必须先审批后执行。
 *
 * <p>与验收要求对应：批准前不得执行高风险 Tool；批准后执行、拒绝后不执行。
 */
class AgentRunnerTest {

    @Test
    void deleteIsNotExecutedBeforeApproval() {
        // 模型第一步就建议 deleteTask(T1)
        HarnessService svc = TestSupport.service(
                TestSupport.script(TestSupport.deleteTask("T1"), Main.finalJson("done")));

        AgentRunner.RunResult result = svc.run("删除任务 T1");

        assertEquals(RunStatus.WAITING_APPROVAL, result.status(), "未批准应停在 WAITING_APPROVAL");
        assertTrue(svc.tasks.findById("T1").isPresent(), "批准前任务不应被删除");

        // 关键：批准前没有 deleteTask 的 TOOL_RESULT（未真正执行）
        List<TraceService.ToolEvent> tools = svc.trace.toolEventsFor(result.runId());
        assertTrue(tools.stream().noneMatch(t -> "deleteTask".equals(t.tool()) && t.success()),
                "批准前不得有成功的 deleteTask 执行");

        // 产生了 APPROVAL_REQUIRED 事件
        assertTrue(svc.trace.eventsFor(result.runId()).stream()
                        .anyMatch(e -> e.type() == TraceEventType.APPROVAL_REQUIRED),
                "应有 APPROVAL_REQUIRED 事件");
    }

    @Test
    void approveExecutesDeleteFromCheckpoint() {
        HarnessService svc = TestSupport.service(
                TestSupport.script(TestSupport.deleteTask("T1"), Main.finalJson("done")));
        AgentRunner.RunResult paused = svc.run("删除任务 T1");
        assertEquals(RunStatus.WAITING_APPROVAL, paused.status());

        AgentRunner.RunResult resumed = svc.resumeAfterApproval(paused.approvalId(), true);

        assertEquals(RunStatus.COMPLETED, resumed.status(), "批准后应恢复到 COMPLETED");
        assertTrue(svc.tasks.findById("T1").isEmpty(), "批准后任务应被真正删除");
        assertTrue(svc.trace.eventsFor(paused.runId()).stream()
                        .anyMatch(e -> e.type() == TraceEventType.APPROVAL_RESOLVED),
                "应有 APPROVAL_RESOLVED 事件");
    }

    @Test
    void rejectSkipsDeleteAndCompletes() {
        HarnessService svc = TestSupport.service(
                TestSupport.script(TestSupport.deleteTask("T1"), Main.finalJson("ok")));
        AgentRunner.RunResult paused = svc.run("删除任务 T1");
        assertEquals(RunStatus.WAITING_APPROVAL, paused.status());

        AgentRunner.RunResult resumed = svc.resumeAfterApproval(paused.approvalId(), false);

        assertEquals(RunStatus.COMPLETED, resumed.status(), "拒绝后应正常结束");
        assertTrue(svc.tasks.findById("T1").isPresent(), "拒绝后任务不应被删除");
        // 拒绝路径不执行 deleteTask → 不应有成功的 deleteTask 工具事件
        assertTrue(svc.trace.toolEventsFor(paused.runId()).stream()
                        .noneMatch(t -> "deleteTask".equals(t.tool()) && t.success()),
                "拒绝后不应执行 deleteTask");
    }

    @Test
    void toolExecutorRefusesDeleteWithoutApproval() {
        // 组件级门禁：即使绕过 Runner 直接调 ToolExecutor，未经批准也拒绝
        HarnessService svc = TestSupport.service(TestSupport.script(Main.finalJson("x")));
        ToolResult denied = svc.toolExec.execute(
                new ToolCall("deleteTask", java.util.Map.of("taskId", "T1")), "r", null, false);
        assertFalse(denied.success(), "未经批准应拒绝");
        assertTrue(svc.tasks.findById("T1").isPresent());

        ToolResult allowed = svc.toolExec.execute(
                new ToolCall("deleteTask", java.util.Map.of("taskId", "T1")), "r", null, true);
        assertTrue(allowed.success(), "批准后应放行");
        assertTrue(svc.tasks.findById("T1").isEmpty());
    }
}