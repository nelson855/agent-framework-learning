# 实现 Prompt 07 — LangGraph Long-running Agent

## 本章

```text
docs/chapters/07_LangGraph_LongRunningAgent.md
```

## 必须读取

```text
AGENTS.md
docs/02_Benchmark_Agent规范.md
docs/04_Framework_Mapping规范.md
docs/05_版本与官方文档策略.md
references/handwritten-agent-v1/long-running-agent
```

## 版本规则

开始前查看 LangGraph 当前官方文档，重点确认：

```text
StateGraph / Graph API
checkpointer / persistence
thread/session config
interrupt / resume
Command 等恢复机制
```

如果 API 与教材描述不同，以当前官方文档为准，并记录 Version Notes。

## 目标目录

```text
langgraph/long-running-agent
```

## 技术栈

- Python 3.12+（如当前 LangGraph 要求不同，以官方兼容范围为准）
- `uv` 优先；没有 uv 时可使用 venv + pip
- LangGraph 当前稳定版本
- SQLite checkpointer（如果官方稳定支持且配置简单）；否则使用官方推荐的可持久化本地方案
- pytest

不要引入复杂 Web 框架。

## Benchmark B 流程

实现：

```text
START
  ↓
planner
  ↓
collect
  ↓
analyze
  ↓
evaluate
  ├─ fail → replan → collect
  └─ pass → human_review
                    ↓
                   END
```

State 至少包含：

```text
run_id
user_goal
plan
current_step
collected_data
analysis
score/evaluation
retry_count
status
```

## 必须演示 1：Checkpoint + Restart + Resume

步骤：

1. 启动任务；
2. 执行到 `human_review`；
3. 触发真正的 interrupt；
4. 退出进程；
5. 重新启动程序；
6. 使用同一个 thread/run 恢复；
7. approve；
8. 继续到 END。

必须在 README 写清实际命令。

## 必须演示 2：失败恢复

人为让 `analyze` 或 `evaluate` 第一次失败一次。

证明恢复后：

- 已经完成且有 checkpoint 的昂贵步骤不会无意义全部重跑；
- retry/replan state 可观察。

## 必须演示 3：HITL Edit 或 Reject

除了 approve，再演示一次：

```text
edit 或 reject
```

观察 state 如何变化。

## Graph 粒度要求

不要把所有逻辑写进一个巨大 node。

也不要过度拆成几十个 node。

每个 node 应对应一个可解释、值得 checkpoint/observe 的步骤。

## 副作用说明

README 必须解释：

- 为什么 resume 与幂等有关；
- 哪些操作需要 idempotency key；
- 为什么 checkpoint 不能自动解决重复副作用。

## Framework Mapping

重点对照手写 Long-running baseline：

```text
CheckpointRepository
Resume logic
Run status
Step executor
Approval state
```

回答哪些被 LangGraph persistence/runtime 替代，哪些没有。

## 测试

至少：

- graph route test；
- evaluator fail → replan test；
- interrupt state test；
- resume test（尽量自动化）。
