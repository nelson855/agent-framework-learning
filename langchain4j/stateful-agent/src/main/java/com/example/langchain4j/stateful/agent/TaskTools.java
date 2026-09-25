package com.example.langchain4j.stateful.agent;

import com.example.langchain4j.stateful.domain.Task;
import com.example.langchain4j.stateful.domain.TaskService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * <p>完整对比见 {@code docs/comparisons/ch05_langchain4j_stateful.md}。
 */
public class TaskTools {

    /** 最近的工具调用记录，供调试台"Recent Tool Calls"展示。 */
    private final List<ToolCallRecord> recentCalls =
            Collections.synchronizedList(new ArrayList<>());

    /** 一条工具调用记录：谁、何时、调了什么、回了什么。 */
    public record ToolCallRecord(String tool, String arguments, String result, long atMillis) {
    }

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
    //     @Tool("...") + @P("...")
    //     // 框架反射读签名 → 自动生成 JSON schema → 自动随请求发给模型
    //   差异：schema 不再手写；Java 方法签名变成"给模型看的说明书"，
    //   名字和描述写得好不好直接决定模型选得准不准。
    //   和 Spring AI 的区别：Spring AI 用 @ToolParam，本模块用 @P，
    //   语义一样，只是注解名字不同。
    //   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#5-schema
    @Tool("用给定的标题创建一个新的学习任务。返回新任务的编号。")
    public String createTask(@P("要创建的任务标题，例如：学习 LangChain4j 记忆") String title) {
        Task task = taskService.createTask(title);
        record("createTask", "title=" + title, task.id());
        return task.id();
    }

    @Tool("按编号查询任务。返回任务标题和状态，查不到会说明找不到。")
    public String getTask(@P("createTask 返回的任务编号，例如：T-1") String taskId) {
        String result = taskService.getTask(taskId)
                .map(task -> "任务 " + task.id() + "，标题=" + task.title() + "，状态=" + task.status())
                .orElse("找不到任务：" + taskId);
        record("getTask", "taskId=" + taskId, result);
        return result;
    }

    @Tool("更新任务状态。状态只能是 OPEN 或 DONE。返回更新后的任务描述。")
    public String updateTaskStatus(
            @P("createTask 返回的任务编号，例如：T-1") String taskId,
            @P("新状态，只能是 OPEN 或 DONE") String status) {
        Task task = taskService.updateTaskStatus(taskId, status);
        String result = "任务 " + task.id() + "，标题=" + task.title() + "，状态=" + task.status();
        record("updateTaskStatus", "taskId=" + taskId + ",status=" + status, result);
        return result;
    }

    /** 调试台读取最近的工具调用，最多返回 limit 条，最新的在前。 */
    public List<ToolCallRecord> recentCalls(int limit) {
        List<ToolCallRecord> snapshot;
        synchronized (recentCalls) {
            snapshot = new ArrayList<>(recentCalls);
        }
        Collections.reverse(snapshot);
        return snapshot.stream().limit(limit).toList();
    }

    private void record(String tool, String args, String result) {
        recentCalls.add(new ToolCallRecord(tool, args, result, Instant.now().toEpochMilli()));
    }
}
