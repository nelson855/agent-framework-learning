package com.example.springai.stateful;

import com.example.springai.stateful.agent.TaskTools;
import com.example.springai.stateful.domain.TaskRepository;
import com.example.springai.stateful.domain.TaskService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TaskTools 工具直调：建任务、查任务、改状态三个工具真实可用。 */
class TaskToolsTest {

    @Test
    void createsTaskAndReturnsId() {
        try (TaskRepository repository = TaskRepository.inMemory()) {
            TaskTools tools = new TaskTools(new TaskService(repository));

            String id = tools.createTask("写周报");

            assertTrue(id.startsWith("T-"));
        }
    }

    @Test
    void returnsTaskDescription() {
        try (TaskRepository repository = TaskRepository.inMemory()) {
            TaskTools tools = new TaskTools(new TaskService(repository));
            String id = tools.createTask("写周报");

            String description = tools.getTask(id);

            assertTrue(description.contains("写周报"));
        }
    }

    @Test
    void updatesTaskStatus() {
        try (TaskRepository repository = TaskRepository.inMemory()) {
            TaskTools tools = new TaskTools(new TaskService(repository));
            String id = tools.createTask("写周报");

            tools.updateTaskStatus(id, "DONE");

            assertTrue(tools.getTask(id).contains("DONE"));
        }
    }
}
