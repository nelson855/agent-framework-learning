package com.example.springai.advanced;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * 进阶演示 Agent：一次提问里同时走 RAG 检索与 MCP 工具调用。
 *
 * <p>@FW-CMP 本类对应手写版 {@code agent-harness/AgentRunner}（循环编排器）。
 * 手写版要显式写"检索→拼提示→调模型→判调用→执行工具→回填"的全流程；
 * 本类只剩一次 {@code prompt(...).call()}，检索由 {@link RetrievalAdvisor}、
 * 工具循环由框架的 ToolCallAdvisor、远程调用由 {@link McpTaskToolCallback} 各自承担。
 *
 * <p>完整对比见 {@code docs/comparisons/ch04_spring_ai_advanced.md}。
 */
public class AdvancedAgent {

    /** 单次 run 的结果：用户输入 + 轨迹 + 最终回答。 */
    public record AgentResult(String userInput, RunTrace trace, String finalAnswer) { }

    private static final String SYSTEM_PROMPT = """
            你是一个任务规范助手。根据用户问题，先参考知识库片段回答规范内容；
            如果用户提供了任务编号，再用 get_task_status 工具查询该任务的当前状态，
            并把规范要求和任务现状一起告诉用户。
            用和用户相同的语言回答。不要透露你的思考过程。
            """;

    private final ChatModel chatModel;
    private final PolicyKnowledgeBase knowledgeBase;
    private final TaskMcpClient mcpClient;

    public AdvancedAgent(ChatModel chatModel, PolicyKnowledgeBase knowledgeBase, TaskMcpClient mcpClient) {
        this.chatModel = chatModel;
        this.knowledgeBase = knowledgeBase;
        this.mcpClient = mcpClient;
    }

    /**
     * 处理一次用户提问。检索与工具调用都发生在框架回调里，
     * 本方法只负责装配与记录首尾两个事件。
     */
    public AgentResult chat(String userInput) {
        RunTrace trace = new RunTrace();
        List<McpTaskToolCallback> toolCallbacks = new ArrayList<>();
        for (TaskMcpClient.DiscoveredTool tool : mcpClient.discover()) {
            toolCallbacks.add(new McpTaskToolCallback(mcpClient, tool, trace));
        }
        RetrievalAdvisor retrieval = new RetrievalAdvisor(knowledgeBase, trace, 2);

        trace.record(RunTrace.Kind.MODEL_CALL, "prompt=" + abbreviate(userInput),
                Map.of("tools", toolCallbacks.size()));
        // @FW-CMP [TOOL_LOOP] 多轮工具循环的归属（RAG + MCP 版）
        //   手写（agent-harness/AgentRunner.loop）：
        //     for (...) { reply = llm.chat(...); d = parse(reply);
        //       if (d.isFinal()) return ...; toolExec.execute(...); context.add(...); }
        //   框架（下方链式调用）：
        //     .advisors(retrieval)   // 检索增强插在调模型前
        //     .tools(callbacks...)   // MCP 工具当普通 ToolCallback 挂载
        //     .call()                // ToolCallAdvisor 内部递归，直到无工具调用
        //   差异：业务代码看不到"第几轮"，只能从 RunTrace 的事件顺序反推；
        //   注意 MCP 工具对框架是透明的——框架不知道它背后是独立进程。
        //   完整版见 docs/comparisons/ch04_spring_ai_advanced.md#1-tool-loop
        String finalAnswer = ChatClient.create(chatModel)
                .prompt()
                .advisors(retrieval)
                .system(SYSTEM_PROMPT)
                .user(userInput)
                .tools(toolCallbacks.toArray())
                .call()
                .content();
        trace.record(RunTrace.Kind.FINAL_RESPONSE,
                "len=" + (finalAnswer == null ? 0 : finalAnswer.length()));
        return new AgentResult(userInput, trace, finalAnswer);
    }

    private static String abbreviate(String text) {
        if (text != null && text.length() > 60) {
            return text.substring(0, 60) + "…";
        }
        return String.valueOf(text);
    }
}
