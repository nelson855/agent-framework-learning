package com.example.springai.stateful.web.dto;

import java.util.List;

/** 运行状态视图：运行 + 它产生过的计划（含重规划的新计划）。 */
public record RunStateDto(
        String runId,
        String goal,
        String status,
        int currentStep,
        List<PlanDto> plans) {

    /** 一份计划视图。 */
    public record PlanDto(String planId, List<StepDto> steps) {
    }

    /** 一步视图。 */
    public record StepDto(String stepId, String description, String status) {
    }
}
