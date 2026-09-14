package com.example.springai.basic;

import java.util.function.Consumer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 暴露给模型的工具。方法是普通 Java 方法，框架负责生成参数 schema、
 * 分派调用、把返回结果送回模型。
 *
 * <p>对照手写版：@Tool 一行注解替代了 Tool（定义）+ ToolDefinition（给模型看的说明书）
 * + ToolRegistry（查表分派执行、拼工具说明进系统提示）三个类的活；框架也不再需要
 * AgentDecisionParser，因为模型返回的是标准格式工具调用，原生读得懂，
 * 不用再从纯文本里抠“调工具还是收尾”。省掉的是分派 glue code；
 * 留给你的是工具名、描述、参数说明——写得好不好直接决定模型选得准不准。
 *
 * <p>工具执行出错时返回错误描述字符串（而不是抛异常），
 * 这样框架会把错误作为工具结果送回模型，模型可以换个参数重试。
 * 返回一句话还是抛异常，是业务语义，框架不管，只能你定（见 getTask 查不到时的处理）。
 */
public class TaskTools {

    private final TaskService taskService;
    private final Consumer<ToolCallTrace> traceListener;

    public TaskTools(TaskService taskService, Consumer<ToolCallTrace> traceListener) {
        this.taskService = taskService;
        this.traceListener = traceListener;
    }

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
                // 查不到返回一句话而不是抛异常：给模型留重试机会。手写版失败即失败，
                // 没有“把错误送回模型再试”这条路，这个语义只能由业务定。
                .orElse("找不到任务：" + taskId);
        trace("getTask", "taskId=" + taskId, result);
        return result;
    }

    private void trace(String toolName, String args, String result) {
        System.out.println("[TOOL] " + toolName + " args=[" + args + "] result=[" + result + "]");
        traceListener.accept(new ToolCallTrace(toolName, args, result));
    }
}
