# 实现 Prompt 08 — Google ADK Java Multi-Agent

## 本章

```text
docs/chapters/08_GoogleADK_MultiAgent.md
```

## 必须读取

```text
AGENTS.md
docs/02_Benchmark_Agent规范.md
docs/04_Framework_Mapping规范.md
docs/05_版本与官方文档策略.md
```

## 版本规则

先查看 Google ADK 当前官方 Java 文档/API Reference。

确认当前正式使用方式：

```text
LlmAgent
Runner
Session service
SequentialAgent
ParallelAgent
LoopAgent
Tools
```

如果名称发生变化，使用官方当前 API，并在 README 记录差异。

## 目标目录

```text
google-adk/multi-agent
```

## 技术栈

- JDK 21
- Maven
- Google ADK Java 当前稳定版本
- JUnit 5
- SQLite 仅用于本地 Task Tool（如果需要）

## Benchmark C

构建：

```text
Coordinator
├── ResearchAgent
├── TaskAgent
└── SummaryAgent
```

### ResearchAgent

输入主题，生成结构化的简短背景信息。

### TaskAgent

可以调用：

```text
create_task
get_task
```

### SummaryAgent

把研究结果 + Task 结果汇总为最终答复。

### Coordinator / workflow

负责组合子 Agent。

## 强制实验 A：Sequential

实现固定顺序：

```text
Research → Task → Summary
```

观察：

- 输入输出如何在 Agent 间传递；
- Session state 如何变化。

## 强制实验 B：Parallel

增加一个极小 parallel demo，例如：

```text
ResearchProduct
ResearchCompetitor
        ↓ parallel
Aggregator
```

不需要与主业务完全合并。

## 强制实验 C：Loop

做一个最多 2~3 轮的改进 loop：

```text
Draft → Review → 不合格则再 Draft
```

必须有限制，禁止无限循环。

## Session / Event 分析

README 必须解释：

```text
一次 agent run
一次 session
一次 event
state delta
```

分别是什么，并映射到第一阶段 State / Conversation / Trace。

## 不要做

- 大型 A2A 分布式部署；
- Vertex/GCP 生产部署；
- 复杂前端；
- 10 个 Agent；
- 把每个普通 Java function 都包装成 Agent。

## Framework Mapping

特别回答：

1. `Runner` 对应第一阶段哪些 Harness 职责？
2. `SequentialAgent` 与硬编码 workflow 有何关系？
3. `ParallelAgent` 的 Context 是共享还是隔离？以当前实际 API/行为为准说明。
4. `LoopAgent` 如何停止？
5. 什么情况下一个子任务应该是 Tool 而不是 Agent？
