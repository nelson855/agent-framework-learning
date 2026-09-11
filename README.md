# AI Agent 框架工程实践

> 第二阶段学习仓库：从“手写 Agent”进入“成熟 Agent Framework / Runtime / Harness”。

本仓库面向已经完成第一阶段 Agent 基础训练的开发者。默认你已经亲手实现并理解：

- LLM 调用与多轮对话
- Structured Output / Tool Calling
- Agent Loop / ReAct
- Workflow / Planning / Plan-and-Replan
- State / Memory / RAG / Context Engineering
- Context Compression
- Checkpoint / Resume / Long-running Agent
- Evaluator / Guardrail / HITL
- Multi-Agent / Handoff
- MCP / Skill
- Tracing / Evaluation / Harness

本阶段不再重复“Agent 是什么”，而是研究：

> **成熟框架如何封装这些能力；不同框架处在哪一层；面对真实项目应该如何选型。**

## 推荐入口

按顺序阅读：

1. `AGENTS.md`
2. `docs/00_教材总纲.md`
3. `docs/01_学习路线与框架地图.md`
4. `docs/02_Benchmark_Agent规范.md`
5. `docs/03_代码仓库架构说明.md`
6. `docs/prompts/00_repository_bootstrap.md`

然后按照章节 + Prompt 顺序学习。

## 核心教学法

这套教材不采用“每个框架做一个毫无关系的 Hello World”。

而是采用三个 Benchmark：

- **Benchmark A — Stateful Agent**：Plain Java / Spring AI / LangChain4j 横向重做。
- **Benchmark B — Long-running Agent**：重点学习 LangGraph 的 State / Checkpoint / Interrupt / Resume。
- **Benchmark C — Multi-Agent**：重点学习 Google ADK 与 Agent SDK 的 Agent composition / delegation / handoff。

其中 Benchmark A/B 的手写版本来自第一阶段 `agent-learning` 仓库，并在初始化时复制到当前仓库的 `references/handwritten-agent-v1/` 作为只读基准。

## 技术路线

Java 主线：

- JDK 21
- Maven
- SQLite（需要持久化时）
- Spring AI
- LangChain4j
- Google ADK Java

允许少量 Python 实验：

- LangGraph
- OpenAI Agents SDK
- 需要时的 Claude Agent SDK / SDK 示例

Python 不是本阶段学习目标，只用于接触框架的一等生态。

## 一个重要原则

**不要把 Codex 当成代写器，而要把它当成实验执行器。**

每个实现 Prompt 都要求 AI 编程工具在完成代码后输出 `Framework Mapping`，回答：

1. 框架替代了哪些手写组件？
2. 哪些责任只是换了 API，并没有消失？
3. 哪些能力仍然属于业务代码？
4. 框架获得便利的同时牺牲了什么控制权？

如果只让项目“跑起来”而没有回答这些问题，本阶段学习就没有完成。
