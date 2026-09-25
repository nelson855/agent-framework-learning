package com.example.langchain4j.stateful.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 助手声明式接口：方法是"想要什么能力"，实现是框架生成的代理。
 *
 * <p>@FW-CMP 本接口对应手写版 {@code StatefulAgentRunner.executeStep} 整套循环 +
 * {@code AgentDecisionParser}（约 80 行）。手写版要自己调模型、自己抠"调工具还是收尾"、
 * 自己把工具结果拼回上下文；本接口只有三行方法签名，循环、分派、回填全由代理负责。
 *
 * <p>完整对比见 {@code docs/comparisons/ch05_langchain4j_stateful.md}。
 */
public interface StatefulAssistant {

    // @FW-CMP [PROMPT] 系统提示词替代流程控制代码
    //   手写：StatefulAgentRunner 里一堆 if/else 决定"先查任务还是先建任务"，
    //         流程写死在 Java 代码里，还要附带工具说明书和 JSON 决策协议。
    //   框架（下方注解）：
    //     @SystemMessage("你是任务助手……")
    //     // 流程不再写代码，写提示词；模型读提示词决定调哪个工具
    //   差异：控制流从"代码分支"变成"提示词约定"；提示词改不好，
    //   模型就会选错工具——调优位置变了。
    //   Spring AI 版把同样的提示词放在 TaskAgentService 的字符串常量里，
    //   本模块放在接口注解上，位置不同，性质相同。
    //   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#1-prompt
    @SystemMessage("""
            你是任务管理助手。用户想创建任务就调用 createTask，
            想查询任务就调用 getTask，想更新状态就调用 updateTaskStatus。
            回答用中文，简短直接。
            """)
    String chat(@MemoryId String memoryId, @UserMessage String message);
}
