# Chapter 04 — Spring AI 进阶：RAG、MCP 与 Observability

## 1. 为什么不在 Chapter 02 一次学完

因为 RAG、MCP、Observability 都会增加 Context 和运行链路。

先理解 Tool Loop，再引入这些能力，才能判断每一层到底增加了什么。

## 2. RAG 映射

第一阶段：

```text
Question
 ↓
Retriever
 ↓
Relevant Docs
 ↓
Context Builder
 ↓
LLM
```

Spring AI 会通过 VectorStore / Retrieval / Advisor 等抽象组织这条链路。

本章不是学 Vector DB 产品，而是观察：

> **检索结果是在什么时机、什么位置进入模型 Context 的？**

## 3. MCP 映射

第一阶段你把 Tool 写在本地 Java 项目中。

MCP 后：

```text
Agent
 ↓
MCP Client
 ↓
MCP Server
 ↓
External Tool / Resource
```

重点不是“协议很酷”，而是工具边界发生了变化：

- Tool discovery；
- schema；
- transport；
- authentication；
- remote failure；
- trust boundary。

## 4. Observability

普通日志告诉你：

```text
HTTP 200
```

Agent observability 更需要：

```text
一次用户请求
  ↓
model call #1
  ↓
tool call
  ↓
tool result
  ↓
model call #2
  ↓
RAG retrieval
  ↓
final answer
```

本章必须能看出一条 Agent run 的层次，而不是只打印字符串。

## 5. Demo

在：

```text
spring-ai/advanced
```

实现一个精简实验，不必复制完整 Stateful Agent。

任务：

```text
“根据本地知识库中的任务处理规范，告诉我如何处理 BLOCKED 任务；
如果需要，再通过一个 MCP Tool 查询当前任务状态。”
```

要求观察：

- RAG 什么时候发生；
- MCP Tool 什么时候发生；
- trace 如何串起两者；
- Context 中是否出现重复内容。

## 6. 常见误区

### RAG = Memory

不是。RAG 主要面向外部知识检索；用户长期偏好属于另一类 memory。

### MCP = Agent Framework

不是。MCP 解决外部能力/上下文互操作，不负责完整 Agent Loop。

### 有 trace = 可观测性完成

不是。还要知道 trace 是否能回答你真正的故障问题。

## 7. 思考题

为什么 MCP Tool 比本地 Java Tool 需要更强的错误和权限设计？

## 8. 答案

因为调用跨越了进程/网络/信任边界，除了业务错误，还增加连接、鉴权、版本、超时、远端权限和数据泄露风险。
