package com.example.springai.advanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Advisor 边界测试：验证检索结果确实被注入用户消息，
 * 且命中情况被记录进 {@link RunTrace}。
 */
class RetrievalAdvisorTest {

    /** 捕获 Advisor 改写后请求的桩链：不调模型，直接回固定回答。 */
    static class CapturingChain implements CallAdvisorChain {
        ChatClientRequest captured;

        @Override
        public ChatClientResponse nextCall(ChatClientRequest request) {
            this.captured = request;
            return new ChatClientResponse(new ChatResponse(
                    List.of(new Generation(new AssistantMessage("ok")))), Map.of());
        }

        @Override
        public List<CallAdvisor> getCallAdvisors() {
            return List.of();
        }

        @Override
        public CallAdvisorChain copy(CallAdvisor after) {
            return this;
        }
    }

    @Test
    void advisorInjectsRetrievedSegmentsIntoUserMessage() {
        PolicyKnowledgeBase kb = new PolicyKnowledgeBase(new KeywordEmbeddingModel());
        RunTrace trace = new RunTrace("test");
        RetrievalAdvisor advisor = new RetrievalAdvisor(kb, trace, 2);
        CapturingChain chain = new CapturingChain();

        advisor.adviseCall(ChatClientRequest.builder()
                .prompt(new Prompt("BLOCKED 任务应该怎么处理")).build(), chain);

        String rewritten = RetrievalAdvisor.extractUserText(chain.captured.prompt());
        assertTrue(rewritten.contains("BLOCKED 任务升级处理"), "应注入命中段落的标题");
        assertTrue(rewritten.contains("4 小时内发起升级"), "应注入命中段落的原文");
        assertTrue(rewritten.contains("BLOCKED 任务应该怎么处理"), "原始提问必须保留");
    }

    @Test
    void advisorRecordsRetrievalEventWithHitCount() {
        PolicyKnowledgeBase kb = new PolicyKnowledgeBase(new KeywordEmbeddingModel());
        RunTrace trace = new RunTrace("test");
        RetrievalAdvisor advisor = new RetrievalAdvisor(kb, trace, 2);

        advisor.adviseCall(ChatClientRequest.builder()
                .prompt(new Prompt("BLOCKED 任务应该怎么处理")).build(), new CapturingChain());

        assertEquals(1, trace.size());
        RunTrace.Event event = trace.events().get(0);
        assertEquals(RunTrace.Kind.RETRIEVAL, event.kind());
        assertEquals(2, event.attrs().get("hitCount"));
    }
}
