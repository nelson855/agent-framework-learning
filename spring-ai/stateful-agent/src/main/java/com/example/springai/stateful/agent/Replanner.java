package com.example.springai.stateful.agent;

import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * 重规划：某步失败后，让模型根据失败原因出新步骤。
 *
 * <p>@FW-CMP [PROMPT] 重规划没有专用 API，全靠提示词讲清楚
 *   手写（手写版 StatefulAgentRunner.replan）：
 *     // 失败时调专门的 replan 逻辑：换提示词、限定"只改失败步骤之后"、
 *     // 步骤数上限 2 次重规划，写死在代码里
 *   框架（下方）：
 *     // 同样没有 Replan 专用类；提示词里把"哪步失败、为什么失败"讲清楚，
 *     // 模型照着出新步骤
 *   差异：连"重规划"这个动作框架都不管——它只管单次结构化输出；
 *   什么算失败、失败几次算完、失败记录怎么写回 plan_step 表，全是业务代码。
 *   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#6-schema-plan
 */
public class Replanner {

    private final ChatClient chatClient;
    private final BeanOutputConverter<Planner.StepList> converter;

    public Replanner(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
        this.converter = new BeanOutputConverter<>(Planner.StepList.class);
    }

    /** 根据失败信息出新步骤清单，不落库。 */
    public List<String> replan(String goal, String failedStep, String failureReason) {
        String content = chatClient.prompt()
                .system("用户目标执行失败，需要重新规划。只输出新的执行步骤清单。"
                        + converter.getFormat())
                .user("目标：" + goal + "\n失败步骤：" + failedStep + "\n失败原因：" + failureReason)
                .call()
                .content();
        Planner.StepList draft = converter.convert(content);
        return draft == null || draft.steps() == null ? List.of() : draft.steps();
    }
}
