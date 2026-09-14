package com.example.springai.basic;

import java.util.List;
import java.util.Optional;

/**
 * 任务业务服务。只做业务规则，不知道调用方是人还是模型。
 *
 * <p>对照手写版：这一层从手写到框架原样保留。框架只接管“流程”（循环、分派），
 * 不碰“意思”（建任务是什么、查任务是什么）。这是“不该被替代”的部分。
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

    public Optional<Task> getTask(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(id.strip());
    }

    public List<Task> listTasks() {
        return repository.findAll();
    }
}
