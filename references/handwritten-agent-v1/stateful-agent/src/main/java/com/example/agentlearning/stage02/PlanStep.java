package com.example.agentlearning.stage02;

/**
 * 计划中的一步。状态<b>可变</b>——由执行器逐步推进，便于观察状态流转。
 */
public final class PlanStep {

    private final String id;
    private final String description;
    private PlanStepStatus status;
    private String failureReason;

    public PlanStep(String id, String description) {
        this(id, description, PlanStepStatus.PENDING, null);
    }

    public PlanStep(String id, String description, PlanStepStatus status, String failureReason) {
        this.id = id;
        this.description = description;
        this.status = status;
        this.failureReason = failureReason;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public PlanStepStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    public void setStatus(PlanStepStatus status) {
        this.status = status;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}