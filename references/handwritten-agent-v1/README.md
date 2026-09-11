# Handwritten Agent Baseline

这个目录用于保存第一阶段 `agent-learning` 仓库中的手写 Agent 冻结快照。

初始化 Prompt 会尝试导入：

```text
stateful-agent/      <- 第一阶段 stages/stage02-stateful-agent
long-running-agent/ <- 第一阶段 stages/stage03-long-running-agent
```

如果存在，也可以可选导入：

```text
agent-harness/       <- 第一阶段 stages/stage04-agent-harness
```

## 用途

这些代码是第二阶段框架学习的 Baseline，用于回答：

- Spring AI 替代了哪些组件？
- LangChain4j 与手写版本的抽象差异是什么？
- LangGraph 的 persistence / checkpoint 与手写实现差在哪？
- Agent SDK / Harness 把运行时责任移到了哪里？

## 只读规则

导入后冻结。任何 AI 编程工具都不得修改该目录。
