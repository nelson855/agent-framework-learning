package com.example.langchain4j.stateful.agent;

import com.example.langchain4j.stateful.domain.Memory;
import com.example.langchain4j.stateful.domain.MessageRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 一次对话的编排：记流水账 → 检索长期记忆 → 调代理 → 记流水账。
 *
 * <p>@FW-CMP [CONTEXT] 上下文从手写对象搬到框架代理
 *   手写（手写版 AgentContext）：
 *     AgentContext context = new AgentContext();
 *     context.addSystem(systemPrompt); context.addHistory(...);
 *     context.addMemory(...); context.addToolResult(...);
 *     // 每次调模型前，手动把 context 里的东西拼成消息列表
 *   框架（本类 + AiServices 代理）：
 *     // 没有 Context 对象了；ChatMemory 管历史，工具结果由代理自动回填，
 *     // 长期记忆在调代理前手工拼前缀
 *   差异：上下文从"一个自己抱着的对象"变成"代理内部维护"；
 *   Spring AI 版用顾问链（MessageChatMemoryAdvisor + MemoryInjectionAdvisor）
 *   做同样的事，本模块用代理 + 手工前缀，链条更短但注入逻辑裸露在外。
 *   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#2-context
 */
public class TaskAgentService {

    /** 每个会话一个记忆窗口，窗口内最多保留 20 条消息。 */
    static final int MAX_WINDOW_MESSAGES = 20;

    private final StatefulAssistant assistant;
    private final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();
    private final TaskTools taskTools;
    private final MemoryService memoryService;
    private final MessageRepository messageRepository;

    public TaskAgentService(
            ChatModel chatModel,
            TaskTools taskTools,
            MemoryService memoryService,
            MessageRepository messageRepository) {
        this.taskTools = taskTools;
        this.memoryService = memoryService;
        this.messageRepository = messageRepository;
        // @FW-CMP [TOOL_LOOP] 内层"模型→工具→模型"循环
        //   手写（手写版 StatefulAgentRunner.executeStep）：
        //     for (int i = 0; i < MAX_STEPS; i++) {
        //         String reply = llm.chat(messages);          // 调模型
        //         Decision d = parse(reply);                  // 抠"调工具还是收尾"
        //         if (d.isFinal()) return d.text();           // 收尾
        //         results.add(tools.execute(d));              // 调工具
        //         messages.add(...);                          // 拼回上下文
        //     }
        //   框架（下方 builder）：
        //     AiServices.builder(...).chatModel(...).tools(...).build()
        //     // 循环、分派、结果回填全在代理里，一次 chat() 调用就行
        //   差异：约 80 行循环加解析消失；代价是循环细节（重试几次、超限怎么办）
        //   藏进代理，调参靠 builder 参数而不是改代码。
        //   Spring AI 版是 ChatClient 一次 .call()（ToolCallingAdvisor 管循环），
        //   本模块是代理一次 chat()，两家藏的是同一件事。
        //   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#3-tool-loop-inner
        this.assistant = AiServices.builder(StatefulAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(this::memoryFor)
                .tools(taskTools)
                .build();
    }

    // @FW-CMP [MEMORY] 多会话记忆隔离：一个会话一个窗口
    //   手写（手写版 MessageRepository + 每次查库）：
    //     // 历史存在 SQLite 里，每次调模型前按 conversationId 查出来拼进去
    //     // 没有"窗口"概念，全量拼，超长了自己截
    //   框架（下方 provider）：
    //     // chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
    //     // 每个 memoryId 独立窗口，超长自动丢最旧的
    //   差异：窗口淘汰从"自己写截断"变成"框架自动丢旧消息"；
    //   代价是窗口是内存的，重启就丢——持久账本仍靠 message 表。
    //   传 conversationId 当 memoryId，一个会话一个窗口，互不串话。
    //   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#7-memory
    ChatMemory memoryFor(Object memoryId) {
        return memories.computeIfAbsent(
                memoryId, id -> MessageWindowChatMemory.withMaxMessages(MAX_WINDOW_MESSAGES));
    }

    /**
     * 跑一步对话：记用户流水 → 检索长期记忆拼前缀 → 调代理 → 记助手流水。
     *
     * <p>用 {@code conversationId} 当 {@code @MemoryId}，一个会话固定进同一个窗口。
     */
    public String chat(String conversationId, String userId, String userText) {
        messageRepository.appendMessage(conversationId, "user", userText);
        String augmented = withMemories(userId, userText);
        String answer = assistant.chat(conversationId, augmented);
        messageRepository.appendMessage(conversationId, "assistant", answer);
        return answer;
    }

    /** 调试台用：看某个会话窗口里模型实际能看到的消息。 */
    public List<String> inspectWindow(String conversationId) {
        ChatMemory memory = memories.get(conversationId);
        if (memory == null) {
            return List.of();
        }
        return memory.messages().stream().map(ChatMessage::toString).toList();
    }

    /** 调试台用：最近的工具调用（代理循环里实际发生的事）。 */
    public List<TaskTools.ToolCallRecord> recentToolCalls(int limit) {
        return taskTools.recentCalls(limit);
    }

    private String withMemories(String userId, String userText) {
        List<Memory> hits = memoryService.retrieve(userId, userText, 3);
        if (hits.isEmpty()) {
            return userText;
        }
        String packed = hits.stream()
                .map(m -> m.memoryKey() + "=" + m.memoryValue())
                .collect(Collectors.joining("；"));
        return "[长期记忆：" + packed + "]\n" + userText;
    }
}
