package com.example.agentlearning.stage03;

import java.util.List;

/**
 * LLM 客户端抽象：给一段消息历史，返回一段文本内容。
 */
public interface LlmClient {

    LlmResponse chat(List<Message> messages);
}