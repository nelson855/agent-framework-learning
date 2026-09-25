package com.example.langchain4j.stateful.domain;

/** 计划中的一步。纯业务值对象。 */
public record PlanStep(String id, String planId, int seq, String description, PlanStepStatus status) {
}
