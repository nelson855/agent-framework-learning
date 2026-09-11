package com.example.agentlearning.stage04;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentRunner：Mini Agent Harness 的编排器。把 Plan / Context / Model / Tool / Approval /
 * Checkpoint / Trace / Evaluator 串成一次可暂停、可恢复的 ReAct 运行。
 *
 * <p><b>HITL 门禁</b>：模型建议 deleteTask 时不会执行——先写 approval_request、保存 Checkpoint、
 * run 置 WAITING_APPROVAL 暂停；人工 approve/reject 后再从 Checkpoint 恢复，批准才真正删除。
 * 删除只可能发生在「批准后」路径，模型绕不过。
 */
public final class AgentRunner {

    private static final int DEFAULT_MAX_STEPS = 12;

    private final HarnessService svc;
    private final int maxSteps;

    AgentRunner(HarnessService svc) {
        this(svc, DEFAULT_MAX_STEPS);
    }

    AgentRunner(HarnessService svc, int maxSteps) {
        this.svc = svc;
        this.maxSteps = maxSteps;
    }

    /** 开始一次新运行，跑到「等待审批」或「完成」为止。 */
    public RunResult run(String goal) {
        String runId = "run-" + System.currentTimeMillis();
        List<PlanStep> plan = svc.planner.plan(goal);
        svc.runStore.create(runId, goal, svc.planner.toJson(plan), System.currentTimeMillis());

        List<Message> context = svc.contextBuilder.build(runId, null, goal, plan, "目标: " + goal);
        int cp = svc.checkpoint.save(runId, 0, context);
        svc.trace.record(TraceEvent.of(runId, null, TraceEventType.CHECKPOINT_SAVED, "checkpoint v#" + cp));

        RunResult result = loop(runId, context, plan, 0);
        if (result.status() != RunStatus.WAITING_APPROVAL) {
            finish(runId, result);
        }
        return new RunResult(result.status(), result.answer(), result.approvalId(), runId);
    }

    /** 人工对审批做出决定后，从 Checkpoint 恢复运行。 */
    public RunResult resumeAfterApproval(int approvalId, boolean approved) {
        ApprovalService.ApprovalRequest req = svc.approval.findById(approvalId).orElseThrow(
                () -> new IllegalArgumentException("无此审批请求: " + approvalId));
        if (!req.isPending()) {
            throw new IllegalArgumentException("该审批请求已处理: id=" + approvalId);
        }
        String runId = req.runId();

        svc.trace.record(TraceEvent.of(runId, null, TraceEventType.APPROVAL_RESOLVED,
                approved ? "批准 #" + approvalId : "拒绝 #" + approvalId));

        List<Message> context = svc.checkpoint.loadLatest(runId).orElseThrow(
                () -> new IllegalStateException("找不到 run 的 Checkpoint: " + runId));
        ToolCall call = ToolJson.fromJson(req.toolCallJson());

        ToolResult result;
        if (approved) {
            svc.approval.approve(approvalId);
            result = svc.toolExec.execute(call, runId, null, true);   // 批准后真正执行删除
        } else {
            svc.approval.reject(approvalId);
            result = ToolResult.fail("被用户拒绝，未执行");
        }
        context.add(Message.user(observation(call, result)));

        RunResult resumed = loop(runId, context, svc.planOf(runId), context.size() / 2);
        finish(runId, resumed);
        return new RunResult(resumed.status(), resumed.answer(), -1, runId);
    }

    // ------------------------------------------------------------------

    private RunResult loop(String runId, List<Message> context, List<PlanStep> plan, int stepFrom) {
        int memoryCount = 0;
        for (int step = stepFrom; step < maxSteps; step++) {
            memoryCount = svc.contextBuilder.injectMemory(context, runId, null, svc.goalOf(runId), memoryCount);

            String reply = svc.model.chat(runId, null, context);
            AgentDecision decision = AgentDecisionParser.parse(reply);

            if (decision.isFinal()) {
                svc.runStore.update(runId, RunStatus.RUNNING, step + 1);
                return RunResult.completed(decision.answer());
            }

            ToolCall call = decision.toolCall();

            // HITL：deleteTask 必须先过人工审批
            if ("deleteTask".equals(call.name())) {
                context.add(Message.assistant(reply));
                int cp = svc.checkpoint.save(runId, step + 1, context);
                svc.trace.record(TraceEvent.of(runId, null, TraceEventType.CHECKPOINT_SAVED,
                        "checkpoint 于审批前 v#" + cp));
                int approvalId = svc.approval.create(runId, call,
                        "Agent 请求删除任务: " + call.arguments(), "HIGH");
                svc.trace.record(TraceEvent.of(runId, null, TraceEventType.APPROVAL_REQUIRED,
                        "deleteTask 需人工批准 #" + approvalId + " " + call.arguments()));
                svc.runStore.update(runId, RunStatus.WAITING_APPROVAL, step + 1);
                return RunResult.waiting(approvalId);
            }

            // Multi-Agent：delegateTo 由 Orchestrator 接管
            ToolResult result;
            if ("delegateTo".equals(call.name())) {
                String worker = strArg(call, "worker");
                String task = strArg(call, "task");
                result = svc.orchestrator.delegate(runId, worker, task);
            } else {
                result = svc.toolExec.execute(call, runId, null, false);
            }

            context.add(Message.assistant(reply));
            context.add(Message.user(observation(call, result)));
            svc.trace.record(TraceEvent.of(runId, null, TraceEventType.STATE_CHANGED,
                    "context→" + context.size()));
            int cp = svc.checkpoint.save(runId, step + 1, context);
            svc.trace.record(TraceEvent.of(runId, null, TraceEventType.CHECKPOINT_SAVED,
                    "checkpoint v#" + cp));
            svc.runStore.update(runId, RunStatus.RUNNING, step + 1);
        }
        svc.runStore.update(runId, RunStatus.MAX_STEP_EXCEEDED, maxSteps);
        return RunResult.maxStepsExceeded();
    }

    private void finish(String runId, RunResult result) {
        // 先置终态，使 Evaluator 读到的是结束状态（不是 RUNNING）
        RunStore.RunRow current = svc.runStore.findByRunId(runId);
        svc.runStore.update(runId, result.status(),
                current == null ? 0 : current.currentStep());

        Evaluator.EvaluationResult eval = svc.evaluator.evaluate(runId);
        svc.trace.record(TraceEvent.of(runId, null, TraceEventType.EVALUATION,
                "pass=" + eval.pass() + " steps=" + eval.steps() + " toolErrors=" + eval.toolErrors()
                        + " approvals=" + eval.approvalCount() + eval.notes()));
        svc.runStore.finish(runId, result.status());
        svc.trace.record(TraceEvent.of(runId, null, TraceEventType.RUN_FINISHED,
                result.status() + (result.answer() != null ? " answer=" + abbreviate(result.answer()) : "")));
    }

    private static String observation(ToolCall call, ToolResult result) {
        return "[observation] " + call + " => " + result.message();
    }

    private static String strArg(ToolCall call, String key) {
        Object v = call.arguments().get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() <= 120 ? s : s.substring(0, 117) + "...");
    }

    /** 一次运行的结果。status=WAITING_APPROVAL 时带 approvalId 供人工决定。 */
    public record RunResult(RunStatus status, String answer, int approvalId, String runId) {

        static RunResult completed(String answer) {
            return new RunResult(RunStatus.COMPLETED, answer, -1, null);
        }

        static RunResult waiting(int approvalId) {
            return new RunResult(RunStatus.WAITING_APPROVAL, null, approvalId, null);
        }

        static RunResult maxStepsExceeded() {
            return new RunResult(RunStatus.MAX_STEP_EXCEEDED, null, -1, null);
        }
    }
}