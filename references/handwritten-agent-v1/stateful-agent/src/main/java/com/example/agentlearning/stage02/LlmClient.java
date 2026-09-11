package com.example.agentlearning.stage02;

import java.util.List;

/**
 * LLM 客户端抽象：给一段消息历史，返回一段文本。
 *
 * <p>只定义这一个"接缝"，就能在真实模型（{@link OpenAiCompatibleLlmClient}）
 * 与确定性测试（{@link FakeLlmClient}）之间自由切换。
 */
public interface LlmClient {

    LlmResponse chat(List<Message> messages);
}