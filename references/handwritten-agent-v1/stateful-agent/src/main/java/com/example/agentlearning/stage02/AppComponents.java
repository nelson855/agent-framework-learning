package com.example.agentlearning.stage02;

/**
 * Stage 02 的应用装配点：把仓库、记忆、规划、运行器组装成完整服务。
 *
 * <p>CLI（{@link Main}）和 Web（{@link WebMain}）都通过这里组装，
 * 保证两端复用同一套核心 Agent 逻辑，不会复制两套实现。
 */
public final class AppComponents {

    public final Database db;
    public final ConversationService conversations;
    public final MessageRepository messages;
    public final AgentRunRepository runs;
    public final MemoryRepository memories;
    public final MemoryRetriever retriever;
    public final MemoryExtractor extractor;
    public final Planner planner;
    public final PlanRepository planRepo;
    public final TaskStore taskStore;
    public final ToolRegistry tools;
    public final Replanner replanner;
    public final StatefulAgentRunner runner;
    public final AgentApplicationService agent;

    private AppComponents(
            Database db,
            ConversationService conversations,
            MessageRepository messages,
            AgentRunRepository runs,
            MemoryRepository memories,
            MemoryRetriever retriever,
            MemoryExtractor extractor,
            Planner planner,
            PlanRepository planRepo,
            TaskStore taskStore,
            ToolRegistry tools,
            Replanner replanner,
            StatefulAgentRunner runner,
            AgentApplicationService agent) {
        this.db = db;
        this.conversations = conversations;
        this.messages = messages;
        this.runs = runs;
        this.memories = memories;
        this.retriever = retriever;
        this.extractor = extractor;
        this.planner = planner;
        this.planRepo = planRepo;
        this.taskStore = taskStore;
        this.tools = tools;
        this.replanner = replanner;
        this.runner = runner;
        this.agent = agent;
    }

    public static final String DEFAULT_USER_ID = "demo-user";

    /** 在已有 Database 上组装全套组件（需提供 LlmClient）。 */
    public static AppComponents build(LlmClient llm, Database db) {
        return build(llm, db, DEFAULT_USER_ID);
    }

    public static AppComponents build(LlmClient llm, Database db, String userId) {
        ConversationRepository conversationRepo = new ConversationRepository(db);
        MessageRepository messages = new MessageRepository(db);
        AgentRunRepository runs = new AgentRunRepository(db);
        MemoryRepository memories = new MemoryRepository(db);
        MemoryRetriever retriever = new MemoryRetriever(memories);
        MemoryExtractor extractor = new MemoryExtractor(llm);
        Planner planner = new Planner(llm);
        PlanRepository planRepo = new PlanRepository(db);
        TaskStore taskStore = new TaskStore(db);
        ToolRegistry tools = TaskTools.createDefault(taskStore);
        Replanner replanner = new Replanner(llm);
        StatefulAgentRunner runner = new StatefulAgentRunner(
                llm, tools, messages, runs, planRepo, retriever, replanner);
        ConversationService conversations = new ConversationService(conversationRepo, messages);
        AgentApplicationService agent = new AgentApplicationService(
                userId, conversations, messages, extractor, memories,
                retriever, planner, planRepo, runner);
        return new AppComponents(
                db, conversations, messages, runs, memories, retriever, extractor,
                planner, planRepo, taskStore, tools, replanner, runner, agent);
    }
}