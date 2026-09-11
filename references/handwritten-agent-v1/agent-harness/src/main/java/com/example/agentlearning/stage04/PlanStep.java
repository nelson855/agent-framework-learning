package com.example.agentlearning.stage04;

/**
 * 计划中的一步：Planner 给出的步骤描述，配合 run 的 current_step 展示进度。
 */
public record PlanStep(String id, String description, PlanStatus status) {

    public enum PlanStatus { OPEN, DONE }

    public static PlanStep pending(String id, String description) {
        return new PlanStep(id, description, PlanStatus.OPEN);
    }

    public PlanStep withStatus(PlanStatus status) {
        return new PlanStep(id, description, status);
    }
}