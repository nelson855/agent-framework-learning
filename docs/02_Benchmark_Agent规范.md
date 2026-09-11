# Benchmark Agent 规范

## 1. 为什么需要 Benchmark

如果 Spring AI 做客服、LangChain4j 做旅游、LangGraph 做股票，你无法判断差异来自：

- 框架；还是
- 业务。

因此本教材建立固定 Benchmark。

# Benchmark A — Stateful Task Assistant

## 目标

复现第一阶段 `stage02-stateful-agent` 的核心语义。

## 最小业务能力

用户可以：

1. 创建任务；
2. 查询任务；
3. 更新任务状态；
4. 让 Agent 根据自然语言选择 Tool；
5. 进行多轮对话；
6. 保存少量长期偏好；
7. 查看当前 Agent State / Plan / Retrieved Memory。

## 建议 Tools

```text
create_task(title, description)
get_task(task_id)
update_task_status(task_id, status)
```

可再增加一个纯函数 Tool：

```text
calculate(expression)
```

## State

至少区分：

- Conversation History
- Agent/Run State
- Plan
- Long-term Memory

不要把它们全部混成一个 messages 列表。

## 持久化

SQLite：

```text
task
conversation
message
memory
```

如果框架提供自己的 ChatMemory，可以使用框架抽象，但业务 Task / Long-term Memory 仍应清楚区分。

## UI

简单 Web Debugger：

```text
左：Chat
右：
- Current State
- Plan
- Retrieved Memory
- Recent Tool Calls
```

Spring AI / LangChain4j 两个版本尽量一致。

# Benchmark B — Long-running Research/Task Agent

## 目标

证明 orchestration runtime 的价值，而不是普通 Tool Calling。

流程示例：

```text
START
  ↓
Plan
  ↓
Collect Data
  ↓
Analyze
  ↓
Evaluate
  ├─ pass → END
  └─ fail → Replan → Collect Data
```

必须可演示：

- checkpoint；
- 人工 interrupt；
- resume；
- 失败后从成功节点恢复，而非全部重跑；
- state 可查看。

# Benchmark C — Multi-Agent Task Team

## Agent

```text
Coordinator
├── ResearchAgent
├── TaskAgent
└── SummaryAgent
```

至少展示一种：

- Sequential；
- Parallel；
- Loop；
- Handoff / Agent-as-tool。

重点不是“Agent 数量多”，而是观察：

> 谁负责决定下一个 Agent？上下文怎么传？Session 谁管理？
