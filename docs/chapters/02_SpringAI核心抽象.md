# Chapter 02 — Spring AI 核心抽象

## 1. 为什么先学 Spring AI

你是 Java 后端工程师。Spring AI 的价值不只是“Java 调模型”，而是把 AI 能力放进熟悉的 Spring application model：Bean、configuration、observability、advisor、repository integration。

本章不追求 API 全覆盖，只研究：

```text
ChatModel
ChatClient
Prompt / Message
Structured Output
Tool
Tool Calling Loop
Advisor
```

## 2. ChatModel vs ChatClient

可以类比：

```text
ChatModel
≈ 更底层的模型能力接口

ChatClient
≈ 面向应用的调用门面 / fluent client
```

和第一阶段对应：

```text
LlmClient → ChatModel / ChatClient
```

注意：这只是大致映射，不要强行一一等价。

## 3. Tool Calling 的关键变化

第一阶段你自己写：

```text
model response
   ↓
有没有 tool call？
   ↓
lookup tool
   ↓
execute
   ↓
tool result
   ↓
再次调用 model
```

Spring AI 当前的 Tool Calling 体系可以由框架管理递归 loop。

本章必须通过日志/断点确认：

- 业务代码没有自己写 `while`；
- Tool 仍然是真实 Java 方法/Callback；
- 框架负责把 Tool call/result 串回模型；
- 最终 answer 仍来自模型。

## 4. Advisor 是什么

把 Advisor 理解成：

> **围绕一次 ChatClient 调用的可组合 middleware / interceptor。**

它可以：

- 调用前增强 Prompt；
- 加 Memory；
- 做 RAG；
- 记录日志；
- 影响 Tool Calling；
- 调用后检查 response。

和第一阶段的 `ContextBuilder + Middleware + 部分 Harness` 有相似之处。

但 Advisor 不是“所有 Agent 基础设施”的同义词。

## 5. 最小 Demo

做 `spring-ai/basic`：

Tools：

```text
createTask
getTask
```

用户说：

```text
创建一个“学习 Spring AI Advisor”的任务，然后告诉我任务编号。
```

要求：

1. ChatClient 接受自然语言；
2. 模型决定调用 `createTask`；
3. Tool 调用真实 SQLite repository；
4. 最终模型返回自然语言；
5. 控制台输出一次简洁 trace。

## 6. 不要实现

本章暂时不要：

- Long-term Memory；
- RAG；
- MCP；
- Planning；
- Multi-Agent；
- 自定义复杂 Advisor Chain。

原因：先把“框架接管 Tool Loop”看清。

## 7. Framework Mapping 重点

至少回答：

```text
原 AgentRunner 的 while 谁替代？
ToolRegistry 是否还需要？
Tool schema 从哪里来？
Tool execution error 如何反馈？
如何限制 loop？
```

## 8. 思考题

### Q1

Spring AI 自动执行 Tool 后，Tool Design 是否变得不重要？

### Q2

Advisor 和 Servlet Filter 是不是完全一样？

## 9. 答案

### A1

更重要。模型能否稳定选择 Tool，很大程度依赖 Tool 名称、描述、参数 schema、返回结果和权限。

### A2

可以用 middleware 思维类比，但不要等同。Advisor 面向 AI request/response/context 链，有自己的调用语义和状态传播方式。
