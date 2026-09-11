package com.example.agentlearning.stage03;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * Agent 运行时的可恢复状态 —— Checkpoint 保存的对象。
 *
 * <p>包含 Resume 所需的全部关键信息：runId、goal、计划步骤（含状态）、
 * 每步攒下来的结果。刻意<b>不保存</b>渲染好的上下文文本——需要时由
 * {@link ContextBuilder} 从 knowledge / memory / plan 现组装。
 *
 * @param runId        运行标识
 * @param goal         任务目标
 * @param plan         有序计划步骤
 * @param stepResults  已执行步骤的简短结果
 * @param compacted   是否发生过压缩（观察标记）
 */
public record AgentState(
        String runId,
        String goal,
        List<PlanStep> plan,
        List<String> stepResults,
        boolean compacted) {

    /** 下一个还没执行完的步骤（PENDING/RUNNING 都视为未完成）；全部完成返回 -1。 */
    @JsonIgnore
    public int nextPendingStepIndex() {
        for (int i = 0; i < plan.size(); i++) {
            if (plan.get(i).status() != StepStatus.DONE) {
                return i;
            }
        }
        return -1;
    }

    @JsonIgnore
    public boolean isComplete() {
        return nextPendingStepIndex() == -1;
    }

    @JsonIgnore
    public int totalSteps() {
        return plan.size();
    }
}