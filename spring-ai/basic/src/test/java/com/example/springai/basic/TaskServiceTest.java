package com.example.springai.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TaskService 确定性测试：内存库，不依赖真实模型。 */
class TaskServiceTest {

    private TaskRepository repository;
    private TaskService service;

    @BeforeEach
    void setUp() {
        repository = TaskRepository.inMemory();
        service = new TaskService(repository);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void createTaskAssignsIdAndOpenStatus() {
        Task task = service.createTask("学习 Spring AI Advisor");

        assertEquals("T-1", task.id());
        assertEquals("学习 Spring AI Advisor", task.title());
        assertEquals(TaskStatus.OPEN, task.status());
    }

    @Test
    void getTaskFindsPreviouslyCreatedTask() {
        Task created = service.createTask("学习 Spring AI Advisor");

        Optional<Task> found = service.getTask(created.id());

        assertTrue(found.isPresent());
        assertEquals(created, found.get());
    }

    @Test
    void getTaskReturnsEmptyForUnknownId() {
        assertTrue(service.getTask("T-999").isEmpty());
        assertTrue(service.getTask("  ").isEmpty());
        assertTrue(service.getTask(null).isEmpty());
    }

    @Test
    void createTaskRejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class, () -> service.createTask("  "));
        assertThrows(IllegalArgumentException.class, () -> service.createTask(null));
    }
}
