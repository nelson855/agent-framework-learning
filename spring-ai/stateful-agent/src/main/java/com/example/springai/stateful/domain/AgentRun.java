package com.example.springai.stateful.domain;

/** 一次运行：用户一个目标对应一条运行记录，记着走到第几步、什么状态。 */
public record AgentRun(String runId, String conversationId, String goal, RunStatus status, int currentStep) {
}
