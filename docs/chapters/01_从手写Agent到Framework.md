# Chapter 01 — 从手写 Agent 到 Framework

## 1. 本章为什么存在

第一阶段你做的是“拆机器”：把 Agent 拆成 LLM、Tool、Loop、State、Memory、Checkpoint、Trace 等零件。

第二阶段开始“看整机厂”：框架把很多零件重新包装，你必须知道包装后原来的责任去了哪里。

如果跳过这一步，最容易出现两种误区：

- 看到框架代码很短，就以为复杂性消失了；
- 看到框架抽象很多，就以为它一定比手写 Agent 更高级。

实际上框架主要做三件事：

1. 提供统一抽象；
2. 封装常见运行循环；
3. 提供生态集成与生命周期能力。

它不会替你决定正确的业务目标、Tool 语义、权限边界和评估标准。

## 2. 四类东西先分开

### Application Framework

重点解决：

- 模型接入；
- Prompt / Message；
- Tool Calling；
- Memory；
- RAG；
- 应用集成。

代表：Spring AI、LangChain4j。

### Orchestration Runtime

重点解决：

- State；
- Workflow / Graph；
- Durable execution；
- Checkpoint；
- Pause / Resume；
- HITL。

代表：LangGraph。

### Agent SDK

重点解决：

- Agent / Runner；
- Tool；
- Handoff；
- Guardrail；
- Trace。

代表：OpenAI Agents SDK 等。

### Harness

更接近“Agent 的运行环境”，可能进一步包含：

- Filesystem；
- Shell；
- Sandbox；
- Permission；
- Session；
- Workspace；
- Context management。

## 3. 用你的第一阶段代码做翻译

假设旧项目中有：

```text
AgentRunner.run()
  while (!done) {
    context = contextBuilder.build(...)
    response = llm.chat(context, tools)
    if (response.toolCall()) executeTool(...)
  }
```

框架可能让你写成：

```text
agent.run(userInput)
```

但不要只看 API 行数。

真正要问：

```text
while 循环去哪了？
Tool result 怎么重新进入模型？
最大步数谁控制？
异常怎么处理？
intermediate message 是否保存？
```

## 4. 本章实验

本章不写新 Agent。

让 AI 编程工具读取：

```text
references/handwritten-agent-v1/stateful-agent
references/handwritten-agent-v1/long-running-agent
```

输出一份 `docs/comparisons/handwritten_baseline_analysis.md`。

必须识别：

- Model boundary
- Agent Loop
- Tool system
- State
- Conversation
- Memory
- Persistence
- Checkpoint
- Web adapter
- Trace

并将每个类标注为：

```text
Domain
Application
Agent Infrastructure
Persistence
Adapter
```

## 5. 重点观察

你要得到的不是一张 UML，而是下面的判断能力：

> **哪些代码因为“我们没用框架”才存在？哪些代码无论用不用框架都存在？**

例如 `CreateTaskTool` 的业务逻辑通常不会因为换框架而消失；消失的更可能是 Tool schema 组装、loop dispatch、message glue code。

## 6. 常见误区

### “框架 = Agent”

不对。框架只是构造和运行 Agent 的方式之一。

### “代码少 = 架构更简单”

不一定。复杂度可能进入代理对象、middleware、runtime 或框架内部状态。

### “用了框架就不用懂 Context”

恰好相反。框架越自动，越需要知道它什么时候向模型注入了什么。

## 7. 思考题

### Q1

如果框架自动完成 Tool Loop，你是否还需要理解 Agent Loop？

### Q2

业务 Tool 应不应该直接依赖某个框架的 ToolContext？

## 8. 答案

### A1

需要。否则你无法解释死循环、步数、重复 Tool、intermediate message、异常重试等问题。

### A2

尽量不要让 Domain 核心被框架对象侵入。可以在 adapter 层将框架 Tool 请求转换为业务 service 调用。
