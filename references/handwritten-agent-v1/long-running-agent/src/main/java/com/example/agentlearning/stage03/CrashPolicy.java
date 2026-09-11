package com.example.agentlearning.stage03;

/**
 * 崩溃/中断策略：给定「即将执行」的步骤下标，决定是否在该步骤<b>执行前</b>抛出
 * {@link SimulatedCrashException} 模拟中断。默认从不中断。
 */
@FunctionalInterface
public interface CrashPolicy {

    CrashPolicy NEVER = stepIndex -> false;

    boolean shouldCrash(int stepIndex);
}