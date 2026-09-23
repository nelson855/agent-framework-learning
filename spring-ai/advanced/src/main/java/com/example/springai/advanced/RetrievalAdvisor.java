package com.example.springai.advanced;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * RAG 检索增强 Advisor：把知识库命中拼进用户提问，再放行下游。
 *
 * <p>@FW-CMP 本类对应手写版 {@code agent-harness/ContextBuilder}（上下文组装器）。
 * 手写版在调模型前手动拼"系统提示 + 检索片段 + 用户问题"三段字符串；
 * 本类挂在 Advisor 链上，框架在调模型前自动回调 {@code adviseCall}，
 * 检索与拼接发生的位置从"业务代码里写死的一步"变成"链上可插拔的一环"。
 *
 * <p>刻意不用框架自带的 QuestionAnswerAdvisor：它的检索逻辑包在内部看不见，
 * 与本章"观察检索在 Advisor chain 的哪个位置"的教学目标冲突。
 *
 * <p>完整对比见 {@code docs/comparisons/ch04_spring_ai_advanced.md}。
 */
public class RetrievalAdvisor implements CallAdvisor {

    private final PolicyKnowledgeBase knowledgeBase;
    private final RunTrace trace;
    private final int topK;

    public RetrievalAdvisor(PolicyKnowledgeBase knowledgeBase, RunTrace trace, int topK) {
        this.knowledgeBase = knowledgeBase;
        this.trace = trace;
        this.topK = topK;
    }

    @Override
    public String getName() {
        return "RetrievalAdvisor";
    }

    @Override
    public int getOrder() {
        // 检索增强必须跑在调模型之前，顺序要早于 ChatModelCallAdvisor。
        return 0;
    }

    // @FW-CMP [CONTEXT] 检索结果进入 Context 的时机与位置
    //   手写（agent-harness/ContextBuilder.build，大意）：
    //     List<Chunk> hits = knowledge.search(userText);   // 调模型前手动查
    //     prompt = system + hits.join("\n") + userText;     // 手动拼字符串
    //     reply = llm.chat(prompt);                         // 再调模型
    //   框架（下方 adviseCall）：
    //     // 框架在真正调模型前回调本方法，改写后的 request 自动流向下游
    //     return chain.nextCall(augmentedRequest);
    //   差异：拼接时机由框架回调驱动，而不是业务代码直写；
    //   代价：链上有多个 Advisor 时，要盯 getOrder 决定谁先改 prompt。
    //   完整版见 docs/comparisons/ch04_spring_ai_advanced.md#3-context
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String userText = extractUserText(request.prompt());
        List<PolicyKnowledgeBase.Segment> hits = knowledgeBase.search(userText, topK);
        if (!hits.isEmpty()) {
            StringBuilder augmented = new StringBuilder("[知识库片段]\n");
            for (int i = 0; i < hits.size(); i++) {
                PolicyKnowledgeBase.Segment hit = hits.get(i);
                augmented.append("--- 片段 ").append(i + 1)
                        .append("（").append(hit.title()).append("）---\n")
                        .append(hit.text()).append('\n');
            }
            augmented.append("[用户提问]\n").append(userText);
            request = request.mutate()
                    .prompt(withRewrittenUserMessage(request.prompt(), augmented.toString()))
                    .build();
        }
        trace.record(RunTrace.Kind.RETRIEVAL,
                "hits=" + hits.size() + " sections=" + hits.stream().map(PolicyKnowledgeBase.Segment::title).toList(),
                Map.of("topK", topK, "hitCount", hits.size()));
        return chain.nextCall(request);
    }

    /** 取最后一条用户消息的文本；没有用户消息时返回空串（检索自然无命中）。 */
    static String extractUserText(Prompt prompt) {
        List<Message> instructions = prompt.getInstructions();
        for (int i = instructions.size() - 1; i >= 0; i--) {
            if (instructions.get(i) instanceof UserMessage user) {
                return user.getText() == null ? "" : user.getText();
            }
        }
        return "";
    }

    /** 把最后一条用户消息替换成增强文本，其余消息原样保留。 */
    private static Prompt withRewrittenUserMessage(Prompt prompt, String newUserText) {
        List<Message> instructions = new ArrayList<>(prompt.getInstructions());
        for (int i = instructions.size() - 1; i >= 0; i--) {
            if (instructions.get(i) instanceof UserMessage) {
                instructions.set(i, new UserMessage(newUserText));
                break;
            }
        }
        return new Prompt(instructions, prompt.getOptions());
    }
}
