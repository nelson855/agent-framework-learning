package com.example.agentlearning.stage03;

/**
 * 一次运行的总体状态。
 */
public enum RunStatus {
    PENDING,
    RUNNING,
    INTERRUPTED,
    COMPLETED,
    FAILED
}