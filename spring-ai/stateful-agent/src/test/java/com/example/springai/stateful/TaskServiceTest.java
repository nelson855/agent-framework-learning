package com.example.springai.stateful;

import com.example.springai.stateful.domain.TaskRepository;
import com.example.springai.stateful.domain.TaskService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** TaskService 业务规则：空标题拒绝，正常标题建任务。 */
class TaskServiceTest {

    @Test
    void rejectsBlankTitle() {
        try (TaskRepository repository = TaskRepository.inMemory()) {
            TaskService service = new TaskService(repository);

            assertThrows(IllegalArgumentException.class, () -> service.createTask("  "));
        }
    }

    @Test
    void createsTaskWithTrimmedTitle() {
        try (TaskRepository repository = TaskRepository.inMemory()) {
            TaskService service = new TaskService(repository);

            var task = service.createTask("  写周报  ");

            assertEquals("写周报", task.title());
        }
    }
}
