# Chapter 05 — 用 LangChain4j 重做 Stateful Agent

## 1. 为什么在 Spring AI 之后学

如果先分别学两个框架的 Hello World，你只会记住 API 名字。

现在你已经有：

```text
Plain Java Baseline
Spring AI Version
```

第三个版本才有比较价值：

```text
LangChain4j Version
```

## 2. 本章重点抽象

重点理解：

```text
ChatModel
AI Services
Tools
ChatMemory / ChatMemoryProvider
RAG / RetrievalAugmentor
Agentic APIs（如当前稳定性适合，仅观察）
```

AI Services 可以先理解为：

> **把 LLM 驱动能力声明成一个 Java service interface，由框架代理实现。**

这对 Java 开发者非常自然，但也会隐藏中间过程。

## 3. 为什么 AI Services 值得单独观察

你可能写：

```java
interface Assistant {
    String chat(String message);
}
```

框架代理背后可能做：

```text
Prompt formatting
ChatMemory
Tool invocation
Output parsing
RAG
```

所以你需要问：

> “一个普通 Java interface 背后，到底装了哪些 Agent 行为？”

## 4. Benchmark A 要求

在：

```text
langchain4j/stateful-agent
```

重做与 Spring AI 尽量一致的：

- Task Tool；
- Conversation；
- Agent State；
- Simple Plan；
- Long-term Memory；
- SQLite；
- Web Debugger。

不要从 Spring AI 模块复制框架相关代码。

## 5. Memory 特别注意

LangChain4j 官方明确区分 History 和 Memory 的概念。

本章要继续区分：

```text
UI History
ChatMemory
Long-term user memory
```

不要因为 `ChatMemory` 名字里有 Memory 就把所有长期记忆都塞进去。

## 6. Agentic 模块

如果当前版本的 agentic module 仍被官方标记为 experimental：

- 可以阅读；
- 可以做很小实验；
- 不要把 Benchmark A 强行重构成依赖实验性 API；
- README 中明确写稳定性状态。

## 7. Framework Mapping 重点

和 Spring AI 不同，本章必须额外回答：

```text
AI Services 隐藏了哪些流程？
声明式 interface 对测试有什么影响？
Tool annotation 与 Spring AI Tool 机制区别是什么？
ChatMemoryProvider 如何关联不同 conversation/user？
```

## 8. 思考题

### Q1

AI Services 越声明式，是不是越适合所有 Agent？

### Q2

Spring AI 和 LangChain4j 能否在同一项目混用？

## 9. 答案

### A1

不是。声明式方式适合常见 service-like AI 能力；复杂编排、细粒度状态机或需要手控 loop 的场景可能需要更底层控制。

### A2

技术上可以，但学习和生产中都应有明确理由。无目的混用会带来两套 Model/Tool/Memory 抽象和更复杂的调试路径。
