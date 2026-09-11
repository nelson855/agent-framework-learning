package com.example.agentlearning.stage04;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 函数式 LLM 客户端：把"模型"实现为一个接收消息历史、返回决策 JSON 的函数。
 *
 * <p>它让测试能确定性模拟 Agent 的关键决策（例如在 guardrail 拦截后改变策略），
 * 代替真实在线 LLM，便于断言执行路径。
 */
public final class FunctionLlmClient implements LlmClient {

    private final Function<List<Message>, String> responder;
    private final List<List<Message>> requests = new ArrayList<>();

    public FunctionLlmClient(Function<List<Message>, String> responder) {
        this.responder = responder;
    }

    @Override
    public LlmResponse chat(List<Message> messages) {
        requests.add(List.copyOf(messages));
        return new LlmResponse(responder.apply(messages));
    }

    public int requestCount() {
        return requests.size();
    }

    public List<Message> requestAt(int i) {
        return requests.get(i);
    }
}