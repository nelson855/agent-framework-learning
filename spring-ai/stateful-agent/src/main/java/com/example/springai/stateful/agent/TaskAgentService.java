package com.example.springai.stateful.agent;

import com.example.springai.stateful.domain.MessageRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;

/**
 * 一次对话的编排：记流水账 → 调模型 → 记流水账。
 *
 * <p>@FW-CMP [CONTEXT] 上下文从手写对象搬到框架顾问链
 *   手写（手写版 AgentContext）：
 *     AgentContext context = new AgentContext();
 *     context.addSystem(systemPrompt); context.addHistory(...);
 *     context.addMemory(...); context.addToolResult(...);
 *     // 每次调模型前，手动把 context 里的东西拼成消息列表
 *   框架（本类 + 顾问链）：
 *     // 没有 Context 对象了；MessageChatMemoryAdvisor 管历史，
 *     // MemoryInjectionAdvisor 管长期记忆，各管一摊
 *   差异：上下文从"一个自己抱着的对象"变成"一条链上各顾问依次加工"；
 *   加新来源不用改编排代码，加个顾问就行。
 *   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#2-context
 */
public class TaskAgentService {

    // @FW-CMP [PROMPT] 系统提示词替代流程控制代码
    //   手写：AgentRunner 里一堆 if/else 决定"先查任务还是先建任务"，
    //         流程写死在 Java 代码里。
    //   框架（下方提示词）：
    //     "你是任务助手。用户想查任务就调 getTask，想建任务就调 createTask……"
    //     // 流程不再写代码，写提示词；模型读提示词决定调哪个工具
    //   差异：控制流从"代码分支"变成"提示词约定"；提示词改不好，
    //   模型就会选错工具——调优位置变了。
    //   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#1-prompt
    private static final String SYSTEM_PROMPT = """
            你是任务管理助手。用户想创建任务就调用 createTask，
            想查询任务就调用 getTask，想更新状态就调用 updateTaskStatus。
            回答用中文，简短直接。
            """;

    private final ChatClient chatClient;
    private final TaskTools taskTools;
    private final MemoryService memoryService;
    private final MessageRepository messageRepository;

    public TaskAgentService(
            org.springframework.ai.chat.model.ChatModel chatModel,
            ChatMemory chatMemory,
            TaskTools taskTools,
            MemoryService memoryService,
            MessageRepository messageRepository) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.taskTools = taskTools;
        this.memoryService = memoryService;
        this.messageRepository = messageRepository;
    }

    // @FW-CMP [TOOL_LOOP] 内层"模型→工具→模型"循环
    //   手写（手写版 StatefulAgentRunner.executeStep）：
    //     for (int i = 0; i < MAX_STEPS; i++) {
    //         String reply = llm.chat(messages);          // 调模型
    //         Decision d = parse(reply);                  // 抠"调工具还是收尾"
    //         if (d.isFinal()) return d.text();           // 收尾
    //         results.add(tools.execute(d));              // 调工具
    //         messages.add(...);                          // 拼回上下文
    //     }
    //   框架（下方链式调用）：
    //     .tools(taskTools).call()
    //     // 循环、分派、结果回填全在 ToolCallingAdvisor 里，调一次就行
    //   差异：约 50 行循环消失；代价是循环细节（重试几次、超限怎么办）
    //   藏进框架，调参靠配置而不是改代码。
    //   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#3-tool-loop-inner
    public String chat(String conversationId, String userId, String userText) {
        messageRepository.appendMessage(conversationId, "user", userText);
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userText)
                .advisors(spec -> spec
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .advisors(new MemoryInjectionAdvisor(memoryService, userId)))
                .tools(taskTools)
                .call()
                .content();
        messageRepository.appendMessage(conversationId, "assistant", answer);
        return answer;
    }
}
