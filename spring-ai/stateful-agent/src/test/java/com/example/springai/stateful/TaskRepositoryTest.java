package com.example.springai.stateful;

import com.example.springai.stateful.domain.TaskRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TaskRepository 存取往返：存进去的任务能按 id 查回来。 */
class TaskRepositoryTest {

    @Test
    void savedTaskCanBeFoundById() {
        try (TaskRepository repository = TaskRepository.inMemory()) {
            var saved = repository.save("写周报");

            var found = repository.findById(saved.id());

            assertTrue(found.isPresent());
            assertEquals("写周报", found.get().title());
        }
    }
}
