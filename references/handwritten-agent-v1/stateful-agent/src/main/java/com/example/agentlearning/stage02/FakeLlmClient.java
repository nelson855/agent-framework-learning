package com.example.agentlearning.stage02;

import java.util.List;

/**
 * 剧本式 Fake LLM 客户端：按预设回复依次返回，用于确定性测试与离线演示。
 *
 * <p>剧本耗尽后<b>重复最后一条</b>——可以方便地制造"死循环"来观察
 * {@link StatefulAgentRunner} 的最大步数拦停。
 */
public final class FakeLlmClient implements LlmClient {

    private final List<String> script;
    private int index;

    public FakeLlmClient(String... script) {
        if (script.length == 0) {
            throw new IllegalArgumentException("剧本不能为空");
        }
        this.script = List.of(script);
    }

    @Override
    public LlmResponse chat(List<Message> messages) {
        String reply = script.get(Math.min(index, script.size() - 1));
        index++;
        return new LlmResponse(reply);
    }

    /** 到目前为止发出的回复条数。 */
    public int requestCount() {
        return index;
    }
}