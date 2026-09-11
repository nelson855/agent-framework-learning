# 实现 Prompt 05 — LangChain4j 重做 Stateful Agent

## 本章

```text
docs/chapters/05_LangChain4j重做StatefulAgent.md
```

## 必须读取

```text
AGENTS.md
docs/02_Benchmark_Agent规范.md
docs/04_Framework_Mapping规范.md
docs/05_版本与官方文档策略.md
references/handwritten-agent-v1/stateful-agent
spring-ai/stateful-agent/README.md
```

## 目标目录

```text
langchain4j/stateful-agent
```

## 技术栈

- JDK 21
- Maven
- LangChain4j 当前稳定版本
- SQLite
- JUnit 5
- Web 层选最简单方式；可以使用轻量 HTTP，也可使用 Spring Boot integration，但 README 必须说明选择原因

## 目标

实现与 Spring AI 版尽量一致的 Benchmark A：

```text
Conversation
Task Tools
Agent State
Simple Plan
ChatMemory
Long-term Memory
SQLite
Web Debugger
```

## 强制学习点

### AI Services

优先使用 AI Services 表达主要 assistant API，并明确：

```text
哪些调用流程被 proxy 隐藏？
```

### Tools

使用官方推荐 Tool 定义方式。

### ChatMemory

如果有多 conversation/user，使用当前官方推荐的 provider / memory-id 方案，而不是共享一个全局 memory。

### Long-term Memory

仍然是独立业务能力，不要拿 ChatMemory 代替。

### RAG

本章默认不要求；除非为了与 Spring AI Stateful Agent 保持某个必要功能，否则不加入。

### Agentic experimental API

如果当前官方仍标记为 experimental：

- Benchmark 主路径不要依赖；
- 可以增加 `experiments/agentic-api` 小实验；
- README 明确状态。

## 业务对齐

尽量复用和 Spring AI 版本相同的：

- Tool 名称语义；
- SQLite 表设计；
- 三个测试输入；
- Web Debugger 展示项。

不要求源码相同。

## README 必须包含

```text
Version Notes
Architecture
AI Services 解释
ChatMemory vs History vs Long-term Memory
Framework Mapping
与 Spring AI 的第一印象对比
```

## Framework Mapping 必答

1. `AiServices` 代理背后负责了什么？
2. Tool loop 谁负责？
3. ChatMemoryProvider 对应第一阶段什么概念？
4. 哪些地方比 Spring AI 更声明式？
5. 声明式 API 带来了哪些调试成本？
