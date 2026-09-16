package com.example.springai.basic;

import java.util.function.Consumer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 暴露给模型的工具。方法是普通 Java 方法，框架负责生成参数 schema、
 * 分派调用、把返回结果送回模型。
 *
 * <p>@FW-CMP 本类对应手写版三个类的合集：
 * {@code Tool}（工具定义）+ {@code ToolDefinition}（给模型看的说明书）
 * + {@code ToolRegistry}（查表分派执行、拼工具说明进系统提示）。
 * 手写版还需要 {@code AgentDecisionParser} 从模型纯文本输出里抠"调工具还是收尾"，
 * 框架下模型返回标准 tool_calls，原生读得懂，这个解析器整个删掉。
 *
 * <p>完整对比见 {@code docs/comparisons/ch02_spring_ai_basic.md}。
 */
public class TaskTools {

    private final TaskService taskService;
    private final Consumer<ToolCallTrace> traceListener;

    public TaskTools(TaskService taskService, Consumer<ToolCallTrace> traceListener) {
        this.taskService = taskService;
        this.traceListener = traceListener;
    }

    // @FW-CMP [SCHEMA] 工具 schema 的生成方式
    //   手写（agent-harness/ToolDefinition + ToolRegistry.toolsInstruction）：
    //     public record ToolDefinition(String name, String description,
    //                                  Map<String, String> parameters) {}
    //     // 每个工具手动 new ToolDefinition("createTask", "...", Map.of("title", "string"))
    //     // ToolRegistry 再用 Jackson 序列化成 JSON 写进系统提示词
    //   框架（下方注解）：
    //     @Tool(name=..., description=...) + @ToolParam(description=...)
    //     // 框架反射读签名 → 自动生成 JSON schema → 自动随请求发给模型
    //   差异：schema 不再手写；Java 方法签名变成"给模型看的说明书"，
    //   名字和描述写得好不好直接决定模型选得准不准。
    //   完整版见 docs/comparisons/ch02_spring_ai_basic.md#4-schema
    @Tool(name = "createTask", description = "用给定的标题创建一个新的学习任务。返回新任务的编号和状态。")
    public String createTask(
            @ToolParam(description = "要创建的任务标题，例如：学习 Spring AI Advisor") String title) {
        String result;
        try {
            Task task = taskService.createTask(title);
            result = "已创建任务 " + task.id() + "，标题=" + task.title() + "，状态=" + task.status();
        } catch (IllegalArgumentException e) {
            result = "创建任务失败：" + e.getMessage();
        }
        trace("createTask", "title=" + title, result);
        return result;
    }

    @Tool(name = "getTask", description = "按编号查询任务。返回任务标题和状态，查不到会说明找不到。")
    public String getTask(
            @ToolParam(description = "createTask 返回的任务编号，例如：T-1") String taskId) {
        String result = taskService.getTask(taskId)
                .map(task -> "任务 " + task.id() + "，标题=" + task.title() + "，状态=" + task.status())
                // @FW-CMP [ERROR_SEMANTIC] 工具错误如何回馈给模型
                //   手写：ToolRegistry.execute 返回 ToolResult.fail(...)，由 AgentRunner
                //         判断"要不要塞回上下文让模型看到"。
                //   框架：直接返回"找不到任务：xxx"字符串，框架自动当工具结果回填给模型，
                //         模型可换参数重试。
                //   差异：错误语义从"业务控制流"变成"提示词工程"——返回一句话还是抛异常，
                //   只能业务自己定，框架不替你想。
                //   完整版见 docs/comparisons/ch02_spring_ai_basic.md#5-error-semantic
                .orElse("找不到任务：" + taskId);
        trace("getTask", "taskId=" + taskId, result);
        return result;
    }

    private void trace(String toolName, String args, String result) {
        System.out.println("[TOOL] " + toolName + " args=[" + args + "] result=[" + result + "]");
        traceListener.accept(new ToolCallTrace(toolName, args, result));
    }
}
