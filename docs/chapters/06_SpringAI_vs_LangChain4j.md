# Chapter 06 — Spring AI vs LangChain4j：Java Agent 框架对照

## 1. 本章不写新 Agent

到这里你应该已经有三个版本：

```text
references/handwritten-agent-v1/stateful-agent
spring-ai/stateful-agent
langchain4j/stateful-agent
```

本章的任务是比较，而不是继续堆功能。

## 2. 第一组问题：谁更像你的 Java 应用框架

比较：

- Spring 配置与 Bean 生命周期；
- 模型 provider 切换；
- Tool 注册；
- Memory 注入；
- Web / Observability 生态；
- 测试方式。

不要只写“Spring AI 更适合 Spring，LangChain4j 更轻量”这种空泛结论。

必须用你实际项目中的类、配置和测试举证。

## 3. 第二组问题：Agent Loop 控制权

对同一个 Tool Calling 场景回答：

```text
Loop 是谁发起的？
最大步数如何限制？
Tool 失败如何反馈？
能否观察 intermediate events？
能否在 Tool 前插入审批？
```

## 4. 第三组问题：Context 与 Memory

比较：

```text
Chat History 怎么存？
模型 Memory 怎么注入？
Long-term Memory 是否需要自建？
RAG 在请求链中的位置在哪里？
```

重点确认：

> **框架提供 memory abstraction，不等于帮你解决所有“记忆产品设计”。**

## 5. 第四组问题：框架侵入性

检查业务类：

```text
TaskService
TaskRepository
LongTermMemoryService
```

有多少框架 annotation / context object 渗入？

如果将来换框架，哪些代码必须重写？

## 6. 第五组问题：可观察性

比较调试一次“模型调用两个 Tool 后回答”的体验：

- 手写版：你能看到什么？
- Spring AI：从哪里看到？
- LangChain4j：从哪里看到？

## 7. 本章产物

生成：

```text
docs/comparisons/java_framework_comparison.md
```

至少包含：

- 对照表；
- 代码量统计；
- 关键 Mapping；
- 同一测试用例结果；
- 适用场景；
- 不适用场景；
- 你的个人暂定选择。

## 8. 思考题

如果你的公司已经有一个大型 Spring Boot 系统，是否意味着永远应该选 Spring AI？

## 9. 答案

不是。“已有 Spring”是很重要的组织与生态因素，但仍要看你需要的模型支持、Agent orchestration、Memory/RAG、长期运行、跨语言服务、团队经验和版本稳定性。选型不是品牌继承。
