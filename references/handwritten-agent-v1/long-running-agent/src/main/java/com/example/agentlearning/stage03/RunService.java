package com.example.agentlearning.stage03;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 03 核心编排器：Web 与 CLI 共用的应用服务。
 *
 * <p>三条 LLM 通路分离，便于测试用不同的确定性脚本分别驱动：
 * <ul>
 *   <li>{@code summarizerLlm} — 上下文压缩摘要；</li>
 *   <li>{@code reportLlm} — 最终交付总结生成（可能给出非法 JSON 以演示 Validator 拒绝）；</li>
 *   <li>{@code evaluatorLlm} — 语义评估（可能先拒绝再通过，以演示有限优化）。</li>
 * </ul>
 *
 * <p>核心能力：创建运行 → 逐步执行（Checkpoint 版本化）→ 受控中断 → Resume → 校验/评估。
 */
public final class RunService {

    public static final int MAX_EVAL_ITERATIONS = 3;

    private final Database db;
    private final RunRepository runs;
    private final CheckpointRepository checkpoints;
    private final CompactionSummaryRepository compactionRepo;
    private final ContextSnapshotRepository snapshotRepo;
    private final KnowledgeRepository knowledgeRepo;
    private final MemoryRepository memoryRepo;
    private final KnowledgeImporter importer;
    private final KnowledgeRetriever knowledgeRetriever;
    private final MemoryRetriever memoryRetriever;
    private final Planner planner;
    private final LongRunningRunner runner;
    private final FinalReportGenerator reportGenerator;
    private final ProgramValidator validator;
    private final LlmEvaluator evaluator;
    private final EvaluationRepository evaluation;

    private RunService(Database db, LlmClient summarizerLlm, LlmClient reportLlm,
            LlmClient evaluatorLlm, ContextPolicy policy) {
        this.db = db;
        this.runs = new RunRepository(db);
        this.checkpoints = new CheckpointRepository(db);
        this.compactionRepo = new CompactionSummaryRepository(db);
        this.snapshotRepo = new ContextSnapshotRepository(db);
        this.knowledgeRepo = new KnowledgeRepository(db);
        this.memoryRepo = new MemoryRepository(db);
        this.importer = new KnowledgeImporter(knowledgeRepo);
        this.knowledgeRetriever = new KnowledgeRetriever(knowledgeRepo);
        this.memoryRetriever = new MemoryRetriever(memoryRepo);
        this.planner = new Planner();

        Compactor compactor = new Compactor(policy, new CompactionSummarizer(summarizerLlm), compactionRepo);
        ContextBuilder contextBuilder = new ContextBuilder(snapshotRepo, compactionRepo);
        CrashPolicy crashPolicy = index -> interruptedStepIndex() == index;
        this.runner = new LongRunningRunner(new StepExecutor(), checkpoints, runs, contextBuilder,
                compactor, knowledgeRetriever, memoryRetriever, crashPolicy);

        this.reportGenerator = new FinalReportGenerator(reportLlm);
        this.validator = new ProgramValidator();
        this.evaluator = new LlmEvaluator(evaluatorLlm);
        this.evaluation = new EvaluationRepository(db);
    }

    public static RunService create(Database db, LlmClient summarizerLlm,
            LlmClient reportLlm, LlmClient evaluatorLlm) {
        return new RunService(db, summarizerLlm, reportLlm, evaluatorLlm, new ContextPolicy());
    }

    public static RunService create(Database db, LlmClient summarizerLlm,
            LlmClient reportLlm, LlmClient evaluatorLlm, ContextPolicy policy) {
        return new RunService(db, summarizerLlm, reportLlm, evaluatorLlm, policy);
    }

    // ---------- 中断控制 ----------

    private volatile int interruptTarget = -1;

    /** 请求：在第 {@code stepIndex} 步（0 基）执行前中断。 */
    public void requestInterrupt(int stepIndex) {
        interruptTarget = stepIndex;
    }

    private int interruptedStepIndex() {
        return interruptTarget;
    }

    // ---------- 运行生命周期 ----------

    /** 导入默认知识文档 + 一条默认长期记忆（幂等）。 */
    public void prepareKnowledge() {
        importer.importDefaults();
        if (memoryRepo.findAll().isEmpty()) {
            memoryRepo.save("user-1", "preference",
                    "长期约定：项目使用 SQLite 存储，所有交付需符合 JSON 规范。", 8);
        }
    }

    /** 创建一次运行：导入知识、建 Run、生成计划、保存初始 Checkpoint v0。 */
    public String createRun(String goal) {
        prepareKnowledge();
        Run run = runs.create(goal);
        List<PlanStep> plan = planner.createPlan(goal);
        AgentState initial = new AgentState(run.runId(), goal, plan, List.of(), false);
        checkpoints.save(initial);
        System.out.println("RUN CREATED " + run.runId() + " goal=" + goal);
        System.out.println("PLAN: " + plan.size() + " steps");
        return run.runId();
    }

    /** 执行一步；若命中中断点在步骤前标记 INTERRUPTED。 */
    public StepOutcome stepRun(String runId) {
        AgentState state = checkpoints.loadLatest(runId)
                .orElseThrow(() -> new IllegalStateException("没有 Checkpoint: " + runId));

        try {
            StepOutcome outcome = runner.step(state);
            interruptTarget = -1;
            return outcome;
        } catch (SimulatedCrashException e) {
            runs.update(runId, RunStatus.INTERRUPTED, null);
            System.out.println("RUN INTERRUPTED at step " + (e.stepIndex() + 1));
            // 保留中断目标一次供观察中断点，随后清除，Resume 可继续
            interruptTarget = -1;
            return new StepOutcome(RunStatus.INTERRUPTED,
                    state.plan().get(e.stepIndex()).id(), e.stepIndex(), e.getMessage());
        }
    }

    /** 从最新 Checkpoint 继续，直到完成或再次命中中断；返回最终状态。 */
    public StepOutcome resumeRun(String runId) {
        StepOutcome last = null;
        for (int i = 0; i < 100; i++) {
            last = stepRun(runId);
            if (last.status() == RunStatus.INTERRUPTED || last.status() == RunStatus.COMPLETED) {
                return last;
            }
        }
        return last;
    }

    /** 生成最终交付总结并执行「校验 + 评估」有限重试；返回每轮日志。 */
    public List<String> evaluateRun(String runId) {
        AgentState state = checkpoints.loadLatest(runId)
                .orElseThrow(() -> new IllegalStateException("没有 Checkpoint: " + runId));
        String goal = state.goal();
        List<String> results = state.stepResults();
        List<String> log = new ArrayList<>();

        for (int iter = 1; iter <= MAX_EVAL_ITERATIONS; iter++) {
            String raw = reportGenerator.generate(goal, results);
            ProgramValidator.ValidationResult vr = validator.validate(raw);
            if (!vr.valid()) {
                evaluation.save(new EvaluationRepository.EvaluationEntry(
                        runId, iter, false, vr.errors(), false, 0, List.of(), raw));
                log.add("[iter " + iter + "] VALIDATOR REJECTED -> " + String.join("; ", vr.errors()));
                continue;
            }
            log.add("[iter " + iter + "] VALIDATOR PASSED");
            EvaluatorFeedback feedback = evaluator.evaluate(vr.report());
            evaluation.save(new EvaluationRepository.EvaluationEntry(
                    runId, iter, true, List.of(), feedback.pass(), feedback.score(),
                    feedback.issues(), raw));
            if (feedback.pass()) {
                log.add("[iter " + iter + "] EVALUATOR PASSED score=" + feedback.score());
                return List.copyOf(log);
            }
            log.add("[iter " + iter + "] EVALUATOR REJECTED score=" + feedback.score()
                    + " -> " + String.join("; ", feedback.issues()));
        }
        log.add("MAX_EVAL_ITERATIONS(" + MAX_EVAL_ITERATIONS + ") EXCEEDED");
        return List.copyOf(log);
    }

    // ---------- 读取（供 Web / CLI） ----------

    public Run getRun(String runId) {
        return runs.findById(runId).orElse(null);
    }

    public AgentState getState(String runId) {
        return checkpoints.loadLatest(runId).orElse(null);
    }

    public List<VersionedCheckpoint> checkpoints(String runId) {
        return checkpoints.listTimeline(runId);
    }

    public List<ContextSnapshotRepository.SimpleSnapshot> contextSnapshots(String runId) {
        return snapshotRepo.listSnapshots(runId);
    }

    public List<ContextSnapshotRepository.DocSummary> ragDocs(String runId) {
        return snapshotRepo.listRagDocs(runId);
    }

    public List<Memory> memoriesSnapshot(String runId) {
        return snapshotRepo.listMemories(runId);
    }

    public List<String> compactionSummaries(String runId) {
        return compactionRepo.renderAll(runId);
    }

    public List<EvaluationRepository.EvaluationEntry> evaluations(String runId) {
        return evaluation.list(runId);
    }
}