package com.example.langchain4j.stateful.domain;

/**
 * 任务业务服务。只做业务规则，不知道调用方是人还是模型。
 */
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public Task createTask(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("task title must not be blank");
        }
        return repository.save(title.strip());
    }

    public java.util.Optional<Task> getTask(String id) {
        if (id == null || id.isBlank()) {
            return java.util.Optional.empty();
        }
        return repository.findById(id.strip());
    }

    public Task updateTaskStatus(String id, String status) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("task id must not be blank");
        }
        TaskStatus next;
        try {
            next = TaskStatus.valueOf(status.strip().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("unknown task status: " + status);
        }
        repository.updateStatus(id.strip(), next);
        return repository.findById(id.strip()).orElseThrow();
    }
}
