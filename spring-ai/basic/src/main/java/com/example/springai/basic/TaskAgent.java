package com.example.springai.basic;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 任务助手。
 *
 * <p>@FW-CMP 本类对应手写版 {@code agent-harness/AgentRunner}（循环编排器）。
 * 手写版用 {@code for + if} 显式推进每一轮"调模型→判 decision→执行工具→回填上下文"，
 * 本类全文无循环，只剩一次 {@code prompt(...).call()}。
 *
 * <p>完整对比见 {@code docs/comparisons/ch02_spring_ai_basic.md}。
 */
public class TaskAgent {

    // @FW-CMP [PROMPT] 流程顺序：代码 → 提示词
    //   手写（手写版没有"系统提示词承载流程"这一说，流程写在 Main 里）：
    //     String goal = "阅读本地规范，创建两个子任务，按规范删除一个高风险任务，...";
    //     AgentRunner.RunResult result = svc.run(goal);
    //     // 步骤靠 Planner 拆成 PlanStep 列表，由 AgentRunner 逐步推进
    //   框架（本类下方 SYSTEM_PROMPT）：
    //     "创建完任务后，再查一次该任务" 写在自然语言里，模型自己按提示走
    //   差异：执行顺序的载体从"Java 代码结构"变成"提示词语义"。
    //   完整版见 docs/comparisons/ch02_spring_ai_basic.md#1-prompt
    private static final String SYSTEM_PROMPT = """
            你是一个任务助手，帮用户管理学习任务。
            创建任务用 createTask 工具，查询任务用 getTask 工具。
            创建完任务后，再查一次该任务，把任务编号和状态告诉用户。
            用和用户相同的语言回答。不要透露你的思考过程。
            """;

    private final ChatModel chatModel;
    private final TaskTools taskTools;
    private final List<ToolCallTrace> toolCalls = new ArrayList<>();

    public TaskAgent(ChatModel chatModel, TaskService taskService) {
        this.chatModel = chatModel;
        this.taskTools = new TaskTools(taskService, toolCalls::add);
    }

    public AgentResult chat(String userInput) {
        toolCalls.clear();
        System.out.println("[USER] " + userInput);
        // @FW-CMP [TOOL_LOOP] 多轮工具循环的归属
        //   手写（agent-harness/AgentRunner.loop，约 50 行，删减后骨架）：
        //     for (int step = 0; step < maxSteps; step++) {
        //       String reply = svc.model.chat(runId, null, context);   // 调模型
        //       AgentDecision d = AgentDecisionParser.parse(reply);    // 解析
        //       if (d.isFinal()) return RunResult.completed(d.answer());
        //       ToolResult r = svc.toolExec.execute(d.toolCall(), ...);// 执行工具
        //       context.add(Message.assistant(reply));                 // 手动回填
        //       context.add(Message.user(observation(call, r)));
        //     }
        //   框架（下方链式调用）：
        //     .tools(taskTools).call()  // 一次写完，ToolCallingAdvisor 内部递归
        //   差异：循环、解析、回填、终止判断全部从业务代码消失；
        //   代价：循环不可见，只能从 [TOOL] 日志反推。
        //   完整版见 docs/comparisons/ch02_spring_ai_basic.md#2-tool-loop
        String finalAnswer = ChatClient.create(chatModel)
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(userInput)
                .tools(taskTools)
                .call()
                .content();
        System.out.println("[FINAL] " + finalAnswer);
        return new AgentResult(userInput, List.copyOf(toolCalls), finalAnswer);
    }

    /** 测试用：直接操作工具对象，不经过模型。 */
    TaskTools taskTools() {
        return taskTools;
    }
}
