package com.example.springai.stateful.agent;

import com.example.springai.stateful.domain.Task;
import com.example.springai.stateful.domain.TaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 暴露给模型的工具。方法是普通 Java 方法，框架负责生成参数 schema、
 * 分派调用、把返回结果送回模型。
 *
 * <p>@FW-CMP 本类对应手写版三个类的合集：
 * {@code Tool}（工具定义）+ {@code ToolDefinition}（给模型看的说明书）
 * + {@code ToolRegistry}（查表分派执行、拼工具说明进系统提示）。
 * 手写版还需要解析器从模型纯文本输出里抠"调工具还是收尾"，
 * 框架下模型返回标准 tool_calls，原生读得懂，这个解析器整个删掉。
 *
 * <p>完整对比见 {@code docs/comparisons/ch03_spring_ai_stateful.md}。
 */
public class TaskTools {

    private final TaskService taskService;

    public TaskTools(TaskService taskService) {
        this.taskService = taskService;
    }

    // @FW-CMP [SCHEMA] 工具 schema 的生成方式
    //   手写（ToolDefinition + ToolRegistry.toolsInstruction）：
    //     public record ToolDefinition(String name, String description,
    //                                  Map<String, String> parameters) {}
    //     // 每个工具手动 new ToolDefinition("createTask", "...", Map.of("title", "string"))
    //     // ToolRegistry 再序列化成 JSON 写进系统提示词
    //   框架（下方注解）：
    //     @Tool(name=..., description=...) + @ToolParam(description=...)
    //     // 框架反射读签名 → 自动生成 JSON schema → 自动随请求发给模型
    //   差异：schema 不再手写；Java 方法签名变成"给模型看的说明书"，
    //   名字和描述写得好不好直接决定模型选得准不准。
    //   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#5-schema
    @Tool(name = "createTask", description = "用给定的标题创建一个新的学习任务。返回新任务的编号。")
    public String createTask(
            @ToolParam(description = "要创建的任务标题，例如：学习 Spring AI Advisor") String title) {
        Task task = taskService.createTask(title);
        return task.id();
    }

    @Tool(name = "getTask", description = "按编号查询任务。返回任务标题和状态，查不到会说明找不到。")
    public String getTask(
            @ToolParam(description = "createTask 返回的任务编号，例如：T-1") String taskId) {
        return taskService.getTask(taskId)
                .map(task -> "任务 " + task.id() + "，标题=" + task.title() + "，状态=" + task.status())
                .orElse("找不到任务：" + taskId);
    }

    @Tool(name = "updateTaskStatus", description = "更新任务状态。状态只能是 OPEN 或 DONE。返回更新后的任务描述。")
    public String updateTaskStatus(
            @ToolParam(description = "createTask 返回的任务编号，例如：T-1") String taskId,
            @ToolParam(description = "新状态，只能是 OPEN 或 DONE") String status) {
        Task task = taskService.updateTaskStatus(taskId, status);
        return "任务 " + task.id() + "，标题=" + task.title() + "，状态=" + task.status();
    }
}
