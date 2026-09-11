# 实现 Prompt 01 — 分析 Handwritten Baseline

## 本章

```text
docs/chapters/01_从手写Agent到Framework.md
```

## 开始前读取

```text
AGENTS.md
docs/04_Framework_Mapping规范.md
references/handwritten-agent-v1/stateful-agent
references/handwritten-agent-v1/long-running-agent
```

## 目标

不写新框架代码。分析第一阶段手写 Agent，并建立后续所有框架的对照基线。

## 任务

### 1. Stateful Agent 分类

把主要类/文件按以下类别归类：

```text
Domain
Application
Agent Infrastructure
Persistence
Adapter/UI
Testing
```

重点识别：

```text
Model client
Agent loop / runner
Tool definition / registry / executor
Conversation
Agent state
Plan
Memory
Repository
Context construction
Web adapter
```

### 2. Long-running Agent 分类

额外识别：

```text
Checkpoint
Resume
Evaluator
Interrupt / Approval
Trace
Retry
```

### 3. 标记“理论上可被框架替代”的代码

分三档：

```text
High: 常见框架通常直接接管
Medium: 框架提供抽象，但仍需大量业务实现
Low: 业务代码，框架不应该替代
```

### 4. 输出报告

创建：

```text
docs/comparisons/handwritten_baseline_analysis.md
```

至少包含：

- 两个 Baseline 架构图（Mermaid 或文本）；
- 类职责表；
- Agent Loop 实际代码位置；
- Context 组装位置；
- Memory/State/History 边界；
- Checkpoint/Resume 机制；
- 未来框架映射候选表。

## 禁止

- 修改 references；
- 引入任何框架依赖；
- “顺手优化”旧代码。

## 验收问题

最后明确回答：

1. 哪 5 类代码最可能被成熟框架显著减少？
2. 哪 5 类责任即使使用框架仍必须由项目负责？
3. 哪些复杂度看起来容易被框架隐藏而不是消失？
