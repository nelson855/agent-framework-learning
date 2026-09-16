package com.example.springai.stateful.agent;

import com.example.springai.stateful.domain.Memory;
import com.example.springai.stateful.domain.MemoryRepository;
import java.util.List;

/**
 * 长期记忆服务：记住用户的跨会话偏好，需要时按关键字取回。
 *
 * <p>@FW-CMP [MEMORY] 长期记忆框架不管，只能自建
 *   手写（手写版 ContextBuilder.injectMemory + MemoryStore）：
 *     // 查 SQLite memory 表，把命中的偏好拼进 AgentContext
 *     memoryStore.find(userId, keywords).forEach(m -> context.addSystem(...));
 *   框架（本类 + MemoryInjectionAdvisor）：
 *     // 框架的 ChatMemory 只是"对话窗口"，不管跨会话偏好；
 *     // 本类只管查库，真正拼进模型上下文的是 MemoryInjectionAdvisor
 *   差异：短期窗口框架包了，长期记忆连存带取带注入全是业务自己的；
 *   ChatMemory 重启就丢，memory 表重启还在，两者别混为一谈。
 *   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#7-memory
 */
public class MemoryService {

    private final MemoryRepository repository;

    public MemoryService(MemoryRepository repository) {
        this.repository = repository;
    }

    public Memory remember(String userId, String memoryKey, String memoryValue) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("user id must not be blank");
        }
        return repository.save(userId.strip(), memoryKey, memoryValue);
    }

    public List<Memory> retrieve(String userId, String query, int limit) {
        if (userId == null || userId.isBlank() || query == null || query.isBlank()) {
            return List.of();
        }
        return repository.findByKeyword(userId.strip(), query.strip(), limit);
    }
}
