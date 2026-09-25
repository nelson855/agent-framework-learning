package com.example.langchain4j.stateful;

import com.example.langchain4j.stateful.agent.TaskTools;
import com.example.langchain4j.stateful.domain.TaskRepository;
import com.example.langchain4j.stateful.domain.TaskService;
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

    @Test
    void recordsRecentCallsForDebugger() {
        try (TaskRepository repository = TaskRepository.inMemory()) {
            TaskTools tools = new TaskTools(new TaskService(repository));

            String id = tools.createTask("写周报");
            tools.getTask(id);

            assertEquals(2, tools.recentCalls(10).size());
            assertEquals("getTask", tools.recentCalls(10).get(0).tool());
        }
    }
}
