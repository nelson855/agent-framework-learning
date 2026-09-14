package com.example.springai.basic;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 任务助手。
 *
 * <p>和手写版差别最大的地方：本类没有任何循环。手写版
 * StatefulAgentRunner.run() 是双层循环（外层按计划步骤推进，内层每步一个 ReAct 小循环，
 * 上限 6 轮，失败还要调 Replanner），而这里只有一次 prompt(...).call()。
 * 多轮“模型 → 工具 → 模型”由框架的 ToolCallingAdvisor 接管：收到含工具调用的回复就
 * 执行工具、把结果追加进会话历史、再调模型，直到某轮不再有工具调用为止。
 * 本次演示实际走了 4 轮，但代码里只有 1 次调用。省掉的是循环写法和 glue code，
 * 代价是循环过程不可见，出问题只能从 [TOOL] 日志反推。
 */
public class TaskAgent {

    // 流程意图从“循环语句”搬到了“提示词”：手写版“先建任务再查询”的顺序写在 while 循环里，
    // 这里写在下面第 18 行 "After creating a task, look it up..."。以后改执行顺序，改这段话，不改代码结构。
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
        // 只调一次：.tools(taskTools) 把工具挂给框架后，“调模型→执行工具→再调模型”
        // 全由 ToolCallingAdvisor 自动重入。手写版这里是 while + for 双层循环，外加手动
        // 拼上下文、手动把工具结果塞回消息列表，那些 glue code 现在都不需要了。
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
