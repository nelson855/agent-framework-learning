package com.example.agentlearning.stage04;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluator：一次 run 结束后，用 Trace 数据量化「结果 + 过程」质量。
 *
 * <p>指标：steps（模型调用次数）、toolCalls、toolErrors、approvalCount，
 * 判定 pass = 正常收敛且无工具错误。删除它，Agent 失去「过程/结果被量化」——
 * 只看 final answer 将忽略多步决策里的失败与成本。
 */
public final class Evaluator {

    private final TraceService trace;
    private final RunStore runs;
    private final ApprovalService approvals;

    public Evaluator(TraceService trace, RunStore runs, ApprovalService approvals) {
        this.trace = trace;
        this.runs = runs;
        this.approvals = approvals;
    }

    /** 评估一次 run。 */
    public EvaluationResult evaluate(String runId) {
        RunStore.RunRow run = runs.findByRunId(runId);

        List<TraceEvent> events = trace.eventsFor(runId);
        int steps = (int) events.stream().filter(e -> e.type() == TraceEventType.MODEL_CALL).count();
        List<TraceService.ToolEvent> toolEvents = trace.toolEventsFor(runId);
        int toolCalls = toolEvents.size();
        int toolErrors = (int) toolEvents.stream().filter(e -> !e.success()).count();
        int approvalCount = (int) approvals.all().stream().filter(a -> a.runId().equals(runId)).count();

        List<String> notes = new ArrayList<>();
        boolean completed = run != null && run.status() == RunStatus.COMPLETED;
        boolean pass = completed && toolErrors == 0;
        if (!completed) {
            notes.add(run == null ? "run 不存在" : "未正常收敛: " + run.status());
        }
        if (toolErrors > 0) {
            notes.add(toolErrors + " 次工具错误");
        }
        if (approvalCount > 0) {
            notes.add(approvalCount + " 次人工审批");
        }

        return new EvaluationResult(pass, steps, toolCalls, toolErrors, approvalCount, List.copyOf(notes));
    }

    public record EvaluationResult(boolean pass, int steps, int toolCalls, int toolErrors,
                                   int approvalCount, List<String> notes) {
    }
}