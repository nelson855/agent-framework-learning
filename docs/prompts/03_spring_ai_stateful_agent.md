# 实现 Prompt 03 — Spring AI 重做 Stateful Agent

## 本章

```text
docs/chapters/03_SpringAI重做StatefulAgent.md
```

## 必须读取

```text
AGENTS.md
docs/02_Benchmark_Agent规范.md
docs/04_Framework_Mapping规范.md
references/handwritten-agent-v1/stateful-agent
spring-ai/basic/README.md
```

## 目标

从零实现：

```text
spring-ai/stateful-agent
```

不要复制 Baseline 的 Agent Infrastructure；只参考业务语义和验收用例。

## 技术栈

- JDK 21
- Maven
- Spring Boot
- 当前稳定 Spring AI
- SQLite
- JUnit 5
- 简单服务端 HTML/JS 或最小静态页面

## Benchmark A 能力

### Conversation

支持创建 conversation 和多轮 chat。

### Task Tools

```text
create_task
get_task
update_task_status
```

### Chat Memory

优先使用当前 Spring AI 正式 ChatMemory / Advisor 机制。

但必须明确区分：

```text
UI Conversation History
Model ChatMemory
Long-term Memory
```

### Long-term Memory

继续自己实现简单 SQLite memory repository，例如：

```text
user_id
memory_key
memory_value
created_at
```

只在与当前请求相关时注入模型 Context。

### Agent State / Plan

实现最小：

```text
runId
conversationId
currentGoal
planSteps
currentStep
status
```

如果 Spring AI 没有合适的 plan abstraction，保留简单自有模型，不要强行伪造框架能力。

### Web Debugger

至少显示：

```text
左：Chat
右：
- Current State
- Plan
- Retrieved Long-term Memory
- Recent Tool Calls
```

## 对照测试

从 Baseline README/测试中选至少 3 个可复用业务场景。

分别记录手写版和 Spring AI 版：

- 行为是否一致；
- 关键差异；
- 哪些基础设施类消失；
- 哪些仍保留。

## 禁止

- 修改 references；
- RAG；
- MCP；
- Multi-Agent；
- LangGraph；
- 把 Long-term Memory 偷换成 ChatMemory。

## README 强制内容

```text
Architecture
Version Notes
How to run
Database schema
Framework Mapping
Baseline Comparison
Known framework limitations
```

## Framework Mapping 必答

1. Agent Loop 谁负责？
2. Message history 谁负责？
3. ChatMemory 与数据库 message/history 的关系是什么？
4. Long-term Memory 为什么还需要自建？
5. Advisor 在这个项目里承担了哪些第一阶段 ContextBuilder/Harness 职责？
6. 哪些业务对象完全不应该知道 Spring AI 存在？
