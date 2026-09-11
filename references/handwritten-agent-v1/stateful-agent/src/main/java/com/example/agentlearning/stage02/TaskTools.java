package com.example.agentlearning.stage02;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stage 02 的工具集：把可被 Agent 调用的工具注册进 {@link ToolRegistry}。
 *
 * <p>每个工具的执行器都是<b>确定性程序逻辑</b>（查库、算数），
 * 模型只负责"决定调哪个、传什么参数"。
 */
public final class TaskTools {

    /** 新任务初始状态。验收任务里 Agent 统计 OPEN 任务数，即统计这个状态。 */
    public static final String STATUS_OPEN = "OPEN";

    private TaskTools() {
    }

    public static ToolRegistry createDefault(TaskStore store) {
        return new ToolRegistry()
                .register(createTask(store))
                .register(getTask(store))
                .register(listTasks(store))
                .register(calculator());
    }

    private static Tool createTask(TaskStore store) {
        return new Tool(
                new ToolDefinition("createTask",
                        "创建一个人工智能学习任务（初始状态 OPEN），返回任务 id",
                        Map.of("title", "string", "description", "string")),
                args -> {
                    String title = (String) args.get("title");
                    String description = String.valueOf(args.getOrDefault("description", ""));
                    if (title == null || title.isBlank()) {
                        return ToolResult.fail("任务标题不能为空");
                    }
                    Task task = new Task("t-" + UUID.randomUUID().toString().substring(0, 8),
                            title, description, STATUS_OPEN, Instant.now().toString());
                    store.insert(task);
                    return ToolResult.ok("已创建任务: " + title
                            + " (id=" + task.id() + ", status=" + task.status() + ")");
                });
    }

    private static Tool getTask(TaskStore store) {
        return new Tool(
                new ToolDefinition("getTask", "按 id 查询单个任务", Map.of("id", "string")),
                args -> {
                    String id = (String) args.get("id");
                    return store.findById(id)
                            .map(t -> ToolResult.ok(formatTask(t)))
                            .orElseGet(() -> ToolResult.fail("未找到任务: " + id));
                });
    }

    private static Tool listTasks(TaskStore store) {
        return new Tool(
                new ToolDefinition("listTasks", "列出所有任务及其状态", Map.of()),
                args -> {
                    List<Task> tasks = store.findAll();
                    if (tasks.isEmpty()) {
                        return ToolResult.ok("当前没有任何任务");
                    }
                    StringBuilder sb = new StringBuilder("共 " + tasks.size() + " 个任务:\n");
                    for (Task t : tasks) {
                        sb.append("- ").append(formatTask(t)).append('\n');
                    }
                    return ToolResult.ok(sb.toString().stripTrailing());
                });
    }

    private static Tool calculator() {
        return new Tool(
                new ToolDefinition("calculator",
                        "计算一个四则运算表达式（支持 + - * / 与括号），返回结果",
                        Map.of("expression", "string")),
                args -> {
                    String expression = (String) args.get("expression");
                    try {
                        return ToolResult.ok(String.valueOf(Calculator.eval(expression)));
                    } catch (RuntimeException e) {
                        return ToolResult.fail("计算失败: " + e.getMessage());
                    }
                });
    }

    private static String formatTask(Task t) {
        return t.title() + " [id=" + t.id() + ", status=" + t.status() + "]";
    }
}