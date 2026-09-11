# Chapter 09 — Agent SDK 与 Harness

## 1. 为什么要单独学这一层

Application Framework 主要帮你“在应用里使用 AI”。

Agent SDK / Harness 更接近：

> **直接提供 Agent Runner、delegation、trace、environment、tool runtime 等运行机制。**

本章主要用两个小实验建立感觉：

- OpenAI Agents SDK：Agent / Runner / Tools / Handoffs / Guardrails / Tracing；
- Claude SDK / Tool Runner / Agent SDK 相关能力：Tool Loop、Session、权限/环境等。

API 变化快，实施前必须看当前官方文档。

## 2. OpenAI Agents SDK 的学习重点

不要把它当“另一个 LangChain”。

重点观察它的小 primitives：

```text
Agent
Runner
Tool
Agent-as-tool / Handoff
Guardrail
Trace
```

### Manager vs Handoff

Manager：

```text
Manager Agent
 ├─ Specialist A as tool
 └─ Specialist B as tool
```

最终用户仍主要与 Manager 交互。

Handoff：

```text
Agent A
  ↓ transfer
Agent B
```

会话控制权转交给另一个 Agent。

两者对应你第一阶段不同 Multi-Agent 模式。

## 3. OpenAI 小实验

在：

```text
sdk-experiments/openai
```

实现：

```text
TriageAgent
 ├─ TaskSpecialist
 └─ ResearchSpecialist
```

至少演示：

- 一次 handoff；
- 一个 guardrail；
- 查看 trace；
- 解释 Runner 替你管理了哪些 loop。

## 4. Claude 小实验

当前 Claude SDK 能力以官方文档为准。

本章至少研究 Tool Runner：

```text
Claude
 ↓
Tool request
 ↓
SDK Tool Runner
 ↓
Tool execution
 ↓
Tool result
 ↓
Claude
```

这恰好对应你第一阶段手写 Tool Loop。

在：

```text
sdk-experiments/claude
```

实现一个小型“文件分析助手”或“任务工具助手”。

如果当前官方 Agent SDK 提供更完整的 session / environment / permission 能力，可增加一个极小实验观察；不要为了追求功能数量做大项目。

## 5. Harness 的边界

Harness 往往比普通 Framework 更靠近运行环境：

```text
Model
Agent Loop
Tools
Filesystem
Shell
MCP
Permission
Session
Context
Tracing
Sandbox
```

你可以把 Coding Agent 想象成：

```text
LLM + 很厚的 Harness
```

而不是“一个特别长的 Prompt”。

## 6. 思考题

### Q1

Agent SDK 有 Runner 后，为什么还可能需要 LangGraph？

### Q2

为什么 Coding Agent 的 Harness 比普通客服 Agent 厚很多？

## 7. 答案

### A1

Runner 能管理 Agent loop/delegation，但复杂 durable workflow、长期 checkpoint、任意 state graph 等可能仍需要更专业的 orchestration runtime。具体取决于 SDK 当前能力。

### A2

Coding Agent 需要真实操作文件、Shell、Git、测试、进程、权限、sandbox、session 和长上下文，这些都属于模型之外的运行环境责任。
