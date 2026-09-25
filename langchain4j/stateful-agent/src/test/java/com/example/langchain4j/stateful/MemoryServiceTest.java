package com.example.langchain4j.stateful;

import com.example.langchain4j.stateful.agent.MemoryService;
import com.example.langchain4j.stateful.domain.MemoryRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MemoryService：长期记忆存取按用户隔离，空查询直接返回空。 */
class MemoryServiceTest {

    @Test
    void remembersAndRetrievesByKeyword() {
        try (MemoryRepository repository = MemoryRepository.inMemory()) {
            MemoryService service = new MemoryService(repository);
            service.remember("u1", "学习时间", "早上学习效率高");

            assertEquals(1, service.retrieve("u1", "学习", 5).size());
            assertTrue(service.retrieve("u1", "运动", 5).isEmpty());
        }
    }

    @Test
    void memoriesAreIsolatedByUser() {
        try (MemoryRepository repository = MemoryRepository.inMemory()) {
            MemoryService service = new MemoryService(repository);
            service.remember("u1", "学习时间", "早上学习效率高");

            assertTrue(service.retrieve("u2", "学习", 5).isEmpty());
        }
    }

    @Test
    void blankQueryReturnsEmpty() {
        try (MemoryRepository repository = MemoryRepository.inMemory()) {
            MemoryService service = new MemoryService(repository);

            assertTrue(service.retrieve("u1", "  ", 5).isEmpty());
        }
    }
}
