package com.example.agentlearning.stage04;

/**
 * 一个待办任务（受 {@code deleteTask} 保护的业务对象）。
 *
 * <p>Guardrail 按状态决定删除策略：OPEN 需要人工批准、DONE 禁止删除、IN_PROGRESS 直接放行。
 */
public record Task(String id, String title, Task.Status status) {

    public enum Status {
        OPEN,
        IN_PROGRESS,
        DONE
    }
}