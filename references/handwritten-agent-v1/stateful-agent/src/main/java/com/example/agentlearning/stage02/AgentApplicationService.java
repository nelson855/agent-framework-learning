package com.example.agentlearning.stage02;

import java.util.List;
import java.util.Optional;

/**
 * Agent 应用服务 —— Stage 02 的核心编排器。
 *
 * <p>每一次 {@code chat(conversationId, userInput)} 做三件事：
 * <ol>
 *   <li><b>Memory</b>：提取用户输入中的偏好/事实，决定是否保存为长期记忆；并检索已有相关记忆；</li>
 *   <li><b>Plan</b>：调用模型为当前目标生成一份结构化计划；</li>
 *   <li><b>Run</b>：交给状态化 Agent 执行计划，每步持久化 state、plan-step status、message。</li>
 * </ol>
 *
 * <p>这三个机制各自对应数据库里不同的表。Web UI 把它们展示在右侧的 State / Plan / Memory 三个区域，
 * 正是为了展示：<b>Conversation History ≠ Agent State ≠ Plan ≠ Long-term Memory。</b>
 */
public final class AgentApplicationService {

    private final String userId;
    private final ConversationService conversationService;
    private final MessageRepository messages;
    private final MemoryExtractor extractor;
    private final MemoryRepository memoryRepo;
    private final MemoryRetriever retriever;
    private final Planner planner;
    private final PlanRepository planRepo;
    private final StatefulAgentRunner runner;

    public AgentApplicationService(
            String userId,
            ConversationService conversationService,
            MessageRepository messages,
            MemoryExtractor extractor,
            MemoryRepository memoryRepo,
            MemoryRetriever retriever,
            Planner planner,
            PlanRepository planRepo,
            StatefulAgentRunner runner) {
        this.userId = userId;
        this.conversationService = conversationService;
        this.messages = messages;
        this.extractor = extractor;
        this.memoryRepo = memoryRepo;
        this.retriever = retriever;
        this.planner = planner;
        this.planRepo = planRepo;
        this.runner = runner;
    }

    /**
     * 对指定会话的一条用户输入执行完整的"提取记忆 + 规划 + 执行"。
     */
    public ChatResult chat(String conversationId, String userInput) {
        // 1) 对话历史：用户消息先落库
        messages.append(conversationId, "user", userInput);

        // 2) Memory 提取：判断值不值得长期保存
        MemoryDecision decision = extractor.extract(userInput);
        boolean memorySaved = false;
        String memoryContent = null;
        if (decision.shouldRemember() && !decision.content().isBlank()) {
            Memory saved = memoryRepo.save(userId, decision.memoryType(), decision.content(), 5);
            memorySaved = true;
            memoryContent = "[" + saved.type() + "] " + saved.content();
        }

        // 3) 规划：为当前目标生成结构化计划
        Plan plan = planner.createPlan(userInput);

        // 4) 执行：持久化状态 + 计划步骤 + 对话历史
        RunResult runResult = runner.run(conversationId, userInput, plan);

        // 5) 本轮检索到的记忆
        List<Memory> retrieved = retriever.retrieve(userInput, 3);

        // 6) 从库里读取最新对话历史
        List<StoredMessage> msgs = messages.findByConversation(conversationId);

        // 7) 最新计划（可能因失败发生过重规划，从库里取最新一份）
        Plan latestPlan = planRepo.findLatestByRun(runResult.runId()).orElse(plan);

        return new ChatResult(
                conversationId,
                runResult.runId(),
                runResult.answer(),
                runResult.run().status(),
                runResult.run().currentStep(),
                latestPlan,
                retrieved,
                msgs,
                memorySaved,
                memoryContent);
    }
}