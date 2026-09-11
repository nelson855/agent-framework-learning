package com.example.agentlearning.stage02;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 的上下文：一段完整、不断增长的消息历史。
 *
 * <p>用户输入、模型每次决策、每次工具执行后的 Observation 都会被追加进去，
 * 并在下一轮完整重新提交给模型——这是 Agent 能"记住自己做过什么"的最小机制。
 */
public final class AgentContext {

    private final List<Message> messages = new ArrayList<>();

    public void addSystem(String content) {
        messages.add(Message.system(content));
    }

    public void addUser(String content) {
        messages.add(Message.user(content));
    }

    public void addAssistant(String modelReply) {
        messages.add(Message.assistant(modelReply));
    }

    /** 追加一次工具执行后的观察（带 {@code [observation]} 前缀）。 */
    public void addObservation(ToolCall call, ToolResult result, boolean success) {
        String text = "[observation] " + call.name() + "(" + call.arguments() + ") => "
                + (success ? "" : "[失败] ") + result.message();
        messages.add(Message.user(text));
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }
}