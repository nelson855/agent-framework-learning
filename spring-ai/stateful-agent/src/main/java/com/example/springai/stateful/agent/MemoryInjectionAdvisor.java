package com.example.springai.stateful.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import com.example.springai.stateful.domain.Memory;

/**
 * 把长期记忆塞进模型上下文的自定义顾问。
 *
 * <p>@FW-CMP [MEMORY] 记忆注入只能手写，没有框架捷径
 *   手写（手写版 ContextBuilder.injectMemory）：
 *     // 查 memory 表 → 直接拼进自己维护的 AgentContext
 *   框架（本类）：
 *     // 查 memory 表 → 包装成 SystemMessage → 塞进 Prompt 再交给链条
 *   差异：框架只管"传话"（Advisor 链），不管"记事"（查库还是业务自己来）；
 *   拼进上下文这一步从"直接改自己的 Context 对象"变成"实现 Advisor 接口、
 *   在链条里换一份新的 Prompt"。
 *   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#7-memory
 */
public class MemoryInjectionAdvisor implements CallAdvisor {

    private final MemoryService memoryService;
    private final String userId;

    public MemoryInjectionAdvisor(MemoryService memoryService, String userId) {
        this.memoryService = memoryService;
        this.userId = userId;
    }

    @Override
    public String getName() {
        return "MemoryInjectionAdvisor";
    }

    @Override
    public int getOrder() {
        return Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 1;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String query = request.prompt().getUserMessage().getText();
        List<Memory> hits = memoryService.retrieve(userId, query, 5);
        if (hits.isEmpty()) {
            return chain.nextCall(request);
        }
        String extra = hits.stream()
                .map(m -> m.memoryKey() + "：" + m.memoryValue())
                .collect(Collectors.joining("\n"));
        List<Message> instructions = new ArrayList<>(request.prompt().getInstructions());
        instructions.add(0, new SystemMessage("用户长期记忆（仅供参考）：\n" + extra));
        Prompt augmented = new Prompt(instructions, request.prompt().getOptions());
        return chain.nextCall(request.mutate().prompt(augmented).build());
    }
}
