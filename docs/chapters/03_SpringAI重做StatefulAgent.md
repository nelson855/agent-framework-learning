# Chapter 03 — 用 Spring AI 重做 Stateful Agent

## 1. 本章目标

这是第二阶段第一个真正重要的综合实验。

你已经有手写版本：

```text
references/handwritten-agent-v1/stateful-agent
```

现在不修改它，而是在：

```text
spring-ai/stateful-agent
```

重新实现相同 Benchmark A。

## 2. 为什么必须“重做”而不是“改造”

如果直接在旧项目里引入 Spring AI，你会留下大量第一阶段 Infrastructure，最后不知道：

- 哪些是框架能力；
- 哪些是历史代码；
- 哪些是两套机制叠加。

因此新项目从零搭建，但业务语义与验收场景尽量保持一致。

## 3. 要保留的业务能力

```text
Conversation
Task Tools
Agent State
Simple Plan
Long-term Memory
SQLite
Web Debugger
```

## 4. 哪些地方优先让 Spring AI 接管

优先使用框架正式能力处理：

- Model abstraction；
- ChatClient；
- Tool schema / invocation；
- Tool calling loop；
- Chat memory（如果当前版本适合）；
- Advisor chain；
- Observability 基础能力。

## 5. 哪些东西不要错误地交给框架

### Domain Task

```text
Task
TaskRepository
TaskService
```

仍然属于你的业务。

### Long-term User Memory

不要因为框架有 `ChatMemory` 就把“用户偏好记忆”与“对话窗口”混为一谈。

第一阶段你已经知道：

```text
Conversation History != Long-term Semantic Memory
```

本章继续保持这个边界。

### Plan

如果 Spring AI 没有直接适合你需求的 planner abstraction，不要为了“框架纯洁”硬塞进去。

可以保留简单 `PlanState`，并由模型 structured output 更新。

## 6. Web Debugger

页面要展示：

```text
Chat
Current Plan
Retrieved Long-term Memory
Recent Tool Calls
Framework-generated trace/observability link or summary
```

页面不是产品 UI。

## 7. 对照实验

必须使用至少 3 个相同输入分别跑：

- 手写 Baseline；
- Spring AI。

记录：

```text
核心业务类数量
Agent Infrastructure 类数量
Tool Loop 代码量
Context 组装位置
Memory 注入位置
调试方式
```

## 8. 关键问题

### “框架帮我把 AgentRunner 删除了，是不是 Agent Loop 不存在了？”

不是。

它只是从你的代码移动到框架 runtime。

### “用了 ChatMemory，还要不要数据库 message 表？”

取决于产品需求。UI History、审计记录和模型 Memory 不是天然相同概念。教材要求你明确区分，而不是为了少写表就混用。

## 9. 思考题

### Q1

框架版本比手写版少了 40% 代码，是否说明维护成本也少 40%？

### Q2

如果你需要在每次敏感 Tool 调用前人工审批，应该把逻辑放在哪？

## 10. 答案

### A1

不能直接推导。框架降低 boilerplate，但带来框架升级、隐式生命周期、调试路径和抽象学习成本。

### A2

应位于 Tool execution 的受控边界，而不是只写 Prompt。可以利用框架 hook/advisor/context 机制，但最终必须有确定性的程序门禁。
