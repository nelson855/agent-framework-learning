package com.example.springai.stateful;

import com.example.springai.stateful.agent.MemoryService;
import com.example.springai.stateful.domain.MemoryRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 长期记忆：按关键字检索，只返回当前用户的记忆。 */
class MemoryServiceTest {

    @Test
    void retrievesMemoryByKeyword() {
        try (MemoryRepository repository = MemoryRepository.inMemory()) {
            MemoryService service = new MemoryService(repository);
            service.remember("u1", "作息偏好", "喜欢早上学习");

            var hits = service.retrieve("u1", "早上", 5);

            assertEquals(1, hits.size());
            assertTrue(hits.get(0).memoryValue().contains("早上"));
        }
    }

    @Test
    void ignoresOtherUsersMemories() {
        try (MemoryRepository repository = MemoryRepository.inMemory()) {
            MemoryService service = new MemoryService(repository);
            service.remember("u2", "作息偏好", "喜欢早上学习");

            var hits = service.retrieve("u1", "早上", 5);

            assertTrue(hits.isEmpty());
        }
    }
}
