package com.example.springai.advanced;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 内存任务存储，MCP Server 的后端数据源。
 *
 * <p>刻意不用数据库：教学 MCP Server 只需要"能查到东西 + 能查不到"两种行为，
 * 内存 Map 足够，且演示时无需准备外部依赖。
 */
public class TaskRepository {

    private final Map<String, TaskRecord> tasks = new LinkedHashMap<>();

    /** 教学演示数据：覆盖 OPEN / BLOCKED / DONE 三种状态。 */
    public static TaskRepository demoData() {
        TaskRepository repository = new TaskRepository();
        repository.save(new TaskRecord("T-1", "学习 Spring AI RAG", TaskStatus.OPEN));
        repository.save(new TaskRecord("T-2", "修复支付回调超时", TaskStatus.BLOCKED));
        repository.save(new TaskRecord("T-3", "清理过期归档脚本", TaskStatus.DONE));
        return repository;
    }

    public void save(TaskRecord record) {
        tasks.put(record.id(), record);
    }

    public Optional<TaskRecord> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tasks.get(id.strip()));
    }
}
