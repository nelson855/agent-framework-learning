package com.example.agentlearning.stage03;

/**
 * 受控教学中断：模拟 Long-running 任务在执行到某一步"被打断"。
 * 不是 JVM 崩溃，而是可被 RunService 捕获并标记 INTERRUPTED，随后通过 Checkpoint Resume。
 */
public final class SimulatedCrashException extends RuntimeException {

    private final int stepIndex;

    public SimulatedCrashException(int stepIndex, String message) {
        super(message);
        this.stepIndex = stepIndex;
    }

    public int stepIndex() {
        return stepIndex;
    }
}