package com.example.agentlearning.stage04;

import java.util.List;
import java.util.Map;

/**
 * HarnessService：应用服务层。把 Harness 的各个组件组装起来，向 Web / CLI / 测试提供统一入口。
 *
 * <p>Web 层只做 HTTP/JSON 转换，核心 Agent 逻辑全部在 {@link AgentRunner} 及其组件里。
 * 删除 Web 层，同一 Service 仍可被 CLI / 测试驱动。
 */
public final class HarnessService {

    static final String DB_URL_DEFAULT = "jdbc:sqlite:data/stage04.db";

    // --- 组件 ---
    public final Database db;
    public final TraceService trace;
    public final RunStore runStore;
    public final Planner planner;
    public final TaskStore tasks;
    public final MemoryStore memory;
    public final KnowledgeStore knowledge;
    public final ToolProvider toolProvider;
    public final ToolExecutor toolExec;
    public final CheckpointStore checkpoint;
    public final ApprovalService approval;
    public final Evaluator evaluator;
    public final ContextBuilder contextBuilder;
    public final ModelClient model;
    public final Orchestrator orchestrator;

    private final List<String> workerNames;

    /** 组装一个 Harness。workerNames 为两个 Worker 的名字（Multi-Agent）。 */
    public HarnessService(Database db, LlmClient orchestratorModel,
                          Map<String, LlmClient> workerModels, List<String> workerNames) {
        this.db = db;
        this.trace = new TraceService(db);
        this.runStore = new RunStore(db);
        this.planner = new Planner();
        this.tasks = sampleTasks();
        this.memory = new MemoryStore();
        this.knowledge = new KnowledgeStore();
        this.toolProvider = new ToolProvider(tasks, memory, knowledge);
        this.toolExec = new ToolExecutor(toolProvider.registry(), trace);
        this.checkpoint = new CheckpointStore(db);
        this.approval = new ApprovalService(db);
        this.evaluator = new Evaluator(trace, runStore, approval);
        this.contextBuilder = new ContextBuilder(toolProvider, memory, trace);
        this.model = new ModelClient(orchestratorModel, trace);
        this.workerNames = List.copyOf(workerNames);

        // 组装 Workers
        java.util.LinkedHashMap<String, WorkerAgent> workers = new java.util.LinkedHashMap<>();
        for (String name : workerNames) {
            LlmClient wm = workerModels.get(name) == null ? orchestratorModel : workerModels.get(name);
            workers.put(name, new WorkerAgent(contextBuilder, new ModelClient(wm, trace),
                    toolExec, trace, 5));
        }
        this.orchestrator = new Orchestrator(workers, trace);
    }

    /** 运行一个复杂任务（跑到等待审批或完成）。 */
    public AgentRunner.RunResult run(String goal) {
        return new AgentRunner(this).run(goal);
    }

    /** 人工审批后从 Checkpoint 恢复。 */
    public AgentRunner.RunResult resumeAfterApproval(int approvalId, boolean approved) {
        return new AgentRunner(this).resumeAfterApproval(approvalId, approved);
    }

    // 供 Web/测试查询
    public List<PlanStep> planOf(String runId) {
        RunStore.RunRow row = runStore.findByRunId(runId);
        return row == null ? List.of() : planner.fromJson(row.planJson());
    }

    public String goalOf(String runId) {
        RunStore.RunRow row = runStore.findByRunId(runId);
        return row == null ? "" : row.goal();
    }

    public List<String> workerNames() {
        return workerNames;
    }

    private static TaskStore sampleTasks() {
        return new TaskStore()
                .add(new Task("T1", "编写用户手册", Task.Status.OPEN))
                .add(new Task("T2", "修复登录 Bug", Task.Status.IN_PROGRESS))
                .add(new Task("T3", "Q3 版本发布", Task.Status.DONE));
    }
}