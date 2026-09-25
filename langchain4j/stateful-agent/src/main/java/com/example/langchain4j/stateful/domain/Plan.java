package com.example.langchain4j.stateful.domain;

import java.util.List;

/** 一份计划：一次目标拆出来的有序步骤。纯业务值对象。 */
public record Plan(String id, String runId, List<PlanStep> steps) {
}
