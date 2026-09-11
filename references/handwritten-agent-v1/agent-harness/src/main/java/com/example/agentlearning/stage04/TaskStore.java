package com.example.agentlearning.stage04;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内存态的任务存储。为教学而简化：不落库、不跨 JVM 重启持久化。
 * 本章重点是「删除受 Guardrail + HITL 保护」，而非任务本身的存储。
 */
public final class TaskStore {

    private final Map<String, Task> tasks = new LinkedHashMap<>();

    public TaskStore add(Task task) {
        tasks.put(task.id(), task);
        return this;
    }

    public Optional<Task> findById(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    /** 删除任务；id 不存在返回 false。 */
    public boolean delete(String id) {
        return tasks.remove(id) != null;
    }

    public int size() {
        return tasks.size();
    }

    public List<Task> all() {
        return List.copyOf(tasks.values());
    }
}