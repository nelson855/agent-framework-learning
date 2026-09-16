package com.example.springai.stateful.agent;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 *  planners：把用户目标拆成步骤清单。拆分靠模型，落库不管。
 *
 * <p>@FW-CMP [SCHEMA] 结构化输出替代手写 JSON 解析
 *   手写（手写版 PlanParser）：
 *     // 模型输出纯文本，自己用 Jackson 抠 JSON，格式一歪就崩，
 *     // 还得写重试和容错
 *   框架（下方 BeanOutputConverter）：
 *     // converter.getFormat() 生成格式说明拼进提示词，
 *     // converter.convert(content) 直接变对象
 *   差异：格式约束从"事后解析碰运气"变成"事前告诉模型照着填"；
 *   代价是步骤结构得先定义成 Java 类型，改结构要改类。
 *   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#6-schema-plan
 */
public class Planner {

    /** 模型只填这个结构，id 和落库由 PlanRepository 负责。 */
    public record StepList(List<String> steps) {
    }

    private final ChatClient chatClient;
    private final BeanOutputConverter<StepList> converter;

    public Planner(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
        this.converter = new BeanOutputConverter<>(StepList.class);
    }

    /** 把目标拆成步骤描述清单，不落库。 */
    public List<String> draftSteps(String goal) {
        String content = chatClient.prompt()
                .system("把用户的目标拆成具体的执行步骤，只输出步骤清单。"
                        + converter.getFormat())
                .user(goal)
                .call()
                .content();
        StepList draft = converter.convert(content);
        return draft == null || draft.steps() == null ? List.of() : draft.steps();
    }
}
