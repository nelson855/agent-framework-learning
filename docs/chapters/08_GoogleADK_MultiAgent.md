# Chapter 08 — Google ADK：Agent-first 与 Multi-Agent Composition

## 1. 本章关注什么

前面的 Spring AI / LangChain4j 更容易从“应用集成”角度理解。

Google ADK 更适合用来观察：

> **把 Agent 本身当成主要编程原语之后，系统如何组合多个 Agent。**

重点抽象：

```text
LlmAgent
Runner
Session
Tool
SequentialAgent
ParallelAgent
LoopAgent
```

具体 API 以实现时官方文档为准。

## 2. 三种确定性编排

### Sequential

```text
A → B → C
```

适合步骤顺序固定。

### Parallel

```text
      ┌→ A
Input ┼→ B
      └→ C
```

适合彼此独立的并行任务。

### Loop

```text
Agent / sub-agents
    ↓
条件不满足
    ↺
```

适合有限迭代。

这些都不等于“让模型自由决定所有事情”。

## 3. Agent-first 的价值

当业务本身天然是：

```text
协调者
研究员
执行员
总结员
```

将 Agent 作为组件可能比在一个巨大 Prompt 里让单 Agent 切换角色更清楚。

但 Multi-Agent 的代价也明显：

- 更多模型调用；
- Context 传递问题；
- 责任边界；
- Debug 难度；
- 成本与延迟。

## 4. Benchmark C

在：

```text
google-adk/multi-agent
```

实现：

```text
Coordinator
├── ResearchAgent
├── TaskAgent
└── SummaryAgent
```

推荐流程：

1. ResearchAgent 产生背景信息；
2. TaskAgent 根据需求创建/查询本地任务；
3. SummaryAgent 汇总；
4. Coordinator 或 workflow agent 负责组合。

至少额外展示一个 ParallelAgent 或 LoopAgent 小场景。

## 5. Session 特别观察

必须回答：

```text
Session 保存什么？
Event 是什么？
Agent 之间如何共享/隔离状态？
一次 run 与一次 session 是什么关系？
```

这与第一阶段的：

```text
Conversation
Agent State
Global Memory
```

非常值得对照。

## 6. 不要为了 Multi-Agent 而 Multi-Agent

如果一个 Tool 就能解决，不要拆一个 Agent。

一个实用判断：

> 当子任务需要独立的 instructions、tools、context 或生命周期时，Agent 化才更有意义。

## 7. 思考题

为什么“角色不同”本身不足以成为拆 Agent 的理由？

## 8. 答案

因为角色 Prompt 可以在单 Agent 内切换。真正值得拆 Agent 的通常是能力、Context、Tool 权限、生命周期或并行性需要独立边界。
