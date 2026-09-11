package com.example.agentlearning.stage03;

/**
 * 计划中的一个步骤。
 *
 * @param id          步骤标识，如 "S1"
 * @param description 该步要做什么
 * @param tool        该步对应的工具名
 * @param args        传给工具的确定性参数
 * @param status      状态
 * @param result      执行结果（DONE 后填充）
 */
public record PlanStep(
        String id,
        String description,
        String tool,
        String args,
        StepStatus status,
        String result) {

    public PlanStep {
        if (status == null) {
            status = StepStatus.PENDING;
        }
    }

    public static PlanStep pending(String id, String description, String tool, String args) {
        return new PlanStep(id, description, tool, args, StepStatus.PENDING, null);
    }

    public PlanStep withStatus(StepStatus next) {
        return new PlanStep(id, description, tool, args, next, result);
    }

    public PlanStep withResult(String nextResult) {
        return new PlanStep(id, description, tool, args, StepStatus.DONE, nextResult);
    }
}