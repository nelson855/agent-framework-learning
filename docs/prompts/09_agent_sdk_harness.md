# 实现 Prompt 09 — Agent SDK 与 Harness 小实验

## 本章

```text
docs/chapters/09_AgentSDK与Harness.md
```

## 开始前读取

```text
AGENTS.md
docs/04_Framework_Mapping规范.md
docs/05_版本与官方文档策略.md
```

## 总原则

这是两个**小实验**，不是再造两个综合平台。

目标是理解：

```text
Agent SDK / Runner / Handoff / Guardrail / Trace
```

和：

```text
Tool Runner / Session / Environment / Permission / Harness
```

## Part A — OpenAI Agents SDK

目录：

```text
sdk-experiments/openai
```

### 版本

先检查官方 Agents SDK 当前稳定 Python 文档与包版本。

### Demo

实现：

```text
TriageAgent
├── TaskSpecialist
└── ResearchSpecialist
```

要求：

1. 至少一次真实 handoff；
2. 至少一个 function tool；
3. 至少一个 input/output guardrail 或当前官方等价机制；
4. 查看一次完整 trace；
5. 对比“manager agent-as-tool”与“handoff”概念，主 Demo 只需实现其中一种，另一种可用极小对照片段。

### README

必须回答：

```text
Runner 替代了什么？
Agent loop 是否还存在？
Handoff 后谁拥有下一轮控制权？
Trace 默认能看到哪些事件？
Guardrail 与 Prompt 禁令有什么本质不同？
```

## Part B — Claude SDK / Tool Runner

目录：

```text
sdk-experiments/claude
```

### 版本

先检查 Claude Platform 当前官方 SDK 文档。

优先保证使用当前正式/推荐的：

```text
Tool Runner 或 Agent SDK 中可稳定演示的 loop 能力
```

不要根据旧记忆强行使用不存在的 API。

### Demo

实现一个极小任务助手：

Tools：

```text
read_local_note(name)
create_local_task(title)
```

目标：观察 SDK 自动处理：

```text
model → tool request → execution → tool result → next model turn
```

如果当前官方 SDK 支持 session / permission / hook，可选择一个最小能力额外展示，但不要做大系统。

### README

必须解释：

```text
Tool Runner 与第一阶段手写 Agent Loop 的一一对应
自动 loop 带来的便利
什么时候必须回退到 manual loop
```

## 跨实验总结

创建：

```text
docs/comparisons/agent_sdk_harness_notes.md
```

回答：

1. Application Framework vs Agent SDK；
2. Runner vs Orchestration Runtime；
3. Tool Runner vs Harness；
4. 为什么 Coding Agent 需要更厚 environment。
