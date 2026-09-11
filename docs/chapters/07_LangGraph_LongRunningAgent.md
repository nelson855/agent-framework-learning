# Chapter 07 — LangGraph：Long-running Agent 与 Durable Orchestration

## 1. 为什么已经有 Java Framework 还要学 LangGraph

Spring AI / LangChain4j 很擅长把“模型 + Tool + Memory + RAG”接进应用。

但当任务变成：

```text
执行半小时
中途暂停
等待人工审批
进程重启
继续执行
失败后不重复昂贵步骤
支持回溯状态
```

问题已经不只是“怎么调模型”，而是“怎么可靠编排一个有状态、长时间运行的流程”。

LangGraph 官方把自己定位为低层 orchestration framework/runtime，核心能力包括 durable execution、persistence、human-in-the-loop、streaming 等。

## 2. State / Node / Edge

第一阶段你写：

```text
State
 ↓
step()
 ↓
new State
```

LangGraph 把它显式化：

```text
Graph State
   ↓
Node
   ↓
State Update
   ↓
Edge / Routing
```

这不是为了画图好看，而是为了让每个状态边界可 checkpoint、可恢复、可观察。

## 3. Checkpoint 的意义

普通程序重启：

```text
从 main() 重新来
```

Durable execution：

```text
读取 thread/checkpoint
 ↓
恢复 graph state
 ↓
从正确执行点继续
```

关键是副作用与确定性。

如果某一步已经真正发送邮件、扣款、写文件，恢复时不能无脑再执行一次。

## 4. Interrupt / HITL

流程：

```text
Agent 决定执行高风险动作
      ↓
interrupt
      ↓
持久化 State
      ↓
等待人工 approve / edit / reject
      ↓
resume
```

这比“前端弹确认框”更本质：后端执行本身真的暂停了。

## 5. Benchmark B

在：

```text
langgraph/long-running-agent
```

实现：

```text
START
 ↓
Planner
 ↓
Collect
 ↓
Analyze
 ↓
Evaluator
 ├─ PASS → Human Review → END
 └─ FAIL → Replan → Collect
```

至少演示：

1. 第一次运行到 Human Review 暂停；
2. 关闭程序；
3. 重新启动；
4. 使用同一 thread/session 恢复；
5. approve 后继续 END。

还要人为让某节点失败一次，证明已完成节点不会全部重跑。

## 6. Graph API 与 Functional API

如果当前 LangGraph 同时提供 Graph API 与 Functional API：

- 主实验优先 Graph API，因为它最适合教学观察 State / Node / Edge；
- 额外小实验可以用 Functional API，观察如何在普通代码结构上加入 persistence / interrupt。

不要同时写两套大项目。

## 7. 与手写 long-running baseline 对比

读取：

```text
references/handwritten-agent-v1/long-running-agent
```

回答：

```text
手写 checkpoint schema → LangGraph checkpointer
手写 resume logic → runtime resume
手写 step/status → graph state/node
手写 approval state → interrupt
```

同时指出：

- 业务副作用幂等仍然是你的责任；
- State schema 设计仍然是你的责任；
- Node 粒度设计仍然是你的责任。

## 8. 思考题

### Q1

是不是所有 Agent 都应该用 LangGraph，反正它更强？

### Q2

为什么 durable execution 特别关心副作用幂等？

## 9. 答案

### A1

不是。简单 Tool Agent 用低层 runtime 可能增加状态、部署和调试成本。复杂度应该与任务匹配。

### A2

恢复/重放可能再次运行某段代码。如果副作用不可安全重复，可能重复发送、重复写入、重复扣费。因此副作用必须设计成可识别、可去重或事务化。
