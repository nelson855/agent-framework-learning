package com.example.agentlearning.stage04;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ToolProvider：向 Harness 提供「Agent 能做什么」的定义与注册表。
 *
 * <p>注册 getTask / listTasks / createTask / getDoc / remember / deleteTask / delegateTo。
 * deleteTask 与 delegateTo 由 {@link AgentRunner} 在其到达 {@link ToolExecutor} 前特殊处理，
 * 因此这里给出定义的执行力是桩；真正的执行与门禁在 {@link ToolExecutor}。
 */
public final class ToolProvider {

    private final TaskStore tasks;
    private final MemoryStore memory;
    private final KnowledgeStore knowledge;
    private final AtomicLong idSeq = new AtomicLong(0);

    public ToolProvider(TaskStore tasks, MemoryStore memory, KnowledgeStore knowledge) {
        this.tasks = tasks;
        this.memory = memory;
        this.knowledge = knowledge;
    }

    /** 构建工具注册表（供 system prompt 与 ToolExecutor 使用）。 */
    public ToolRegistry registry() {
        return new ToolRegistry()
                .register(new Tool(new ToolDefinition("getTask",
                        "按 id 查询任务详情", Map.of("taskId", "string")), args ->
                        {
                            String id = strArg(args, "taskId");
                            Task t = tasks.findById(id).orElse(null);
                            return t == null ? ToolResult.fail("任务不存在: " + id)
                                    : ToolResult.ok(t.id() + " | " + t.title() + " | " + t.status());
                        }))
                .register(new Tool(new ToolDefinition("listTasks",
                        "列出所有任务", Map.of()), args ->
                        {
                            if (tasks.all().isEmpty()) {
                                return ToolResult.ok("（没有任务）");
                            }
                            StringBuilder sb = new StringBuilder();
                            for (Task t : tasks.all()) {
                                sb.append(t.id()).append("|").append(t.title())
                                        .append("|").append(t.status()).append("\n");
                            }
                            return ToolResult.ok(sb.toString().trim());
                        }))
                .register(new Tool(new ToolDefinition("createTask",
                        "创建一个 OPEN 任务", Map.of("title", "string")), args ->
                        {
                            String title = strArg(args, "title");
                            String id = "T" + (tasks.all().size() + (idSeq.incrementAndGet()));
                            Task created = new Task(id, title, Task.Status.OPEN);
                            tasks.add(created);
                            return ToolResult.ok("已创建任务 " + id + "（" + title + ", OPEN）");
                        }))
                .register(new Tool(new ToolDefinition("getDoc",
                        "按标题查询本地知识文档", Map.of("title", "string")), args ->
                        {
                            String title = strArg(args, "title");
                            KnowledgeStore.Doc doc = knowledge.findByTitle(title);
                            return doc == null ? ToolResult.fail("未知文档: " + title)
                                    : ToolResult.ok(doc.content());
                        }))
                .register(new Tool(new ToolDefinition("remember",
                        "写入一条工作记忆(键值对)，后续可检索", Map.of("key", "string", "value", "string")), args ->
                        {
                            String key = strArg(args, "key");
                            String value = strArg(args, "value");
                            return ToolResult.ok(memory.remember(key, value));
                        }))
                .register(new Tool(new ToolDefinition("deleteTask",
                        "删除一个任务（高风险，需人工批准）", Map.of("taskId", "string")), args ->
                        {
                            // 桩执行力：真正删除由 AgentRunner 在批准后放行调用
                            String id = strArg(args, "taskId");
                            boolean removed = tasks.delete(id);
                            return removed ? ToolResult.ok("已删除任务 " + id)
                                    : ToolResult.fail("任务不存在: " + id);
                        }))
                .register(new Tool(new ToolDefinition("delegateTo",
                        "把子任务交接给一个 Worker（Orchestrator 处理，产生 Handoff）",
                        Map.of("worker", "string", "task", "string")), args ->
                        {
                            // 占位；真正由 AgentRunner 拦截并派发
                            return ToolResult.fail("delegateTo 由 Orchestrator 处理");
                        }));
    }

    /** 供 system prompt 展示的工具说明（含全部已注册工具）。 */
    public String toolsInstruction() {
        return registry().toolsInstruction();
    }

    public List<ToolDefinition> definitions() {
        return registry().definitions();
    }

    private static String strArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}