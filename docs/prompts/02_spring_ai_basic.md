# 实现 Prompt 02 — Spring AI Basic：观察框架接管 Tool Loop

## 本章

```text
docs/chapters/02_SpringAI核心抽象.md
```

## 先读取

```text
AGENTS.md
docs/04_Framework_Mapping规范.md
docs/05_版本与官方文档策略.md
docs/comparisons/handwritten_baseline_analysis.md
```

## 版本规则

开始编码前，先检查 Spring AI 当前官方 Reference 和 Maven 依赖。

不要假设教材中的 API 名称永远不变。

在模块 README 记录：

```text
Spring AI version
Spring Boot version
Docs checked date
API differences
```

## 目标目录

```text
spring-ai/basic
```

## 技术栈

- JDK 21
- Maven
- Spring Boot（本章允许并推荐，因为目标就是 Spring AI 工程集成）
- Spring AI
- SQLite JDBC 或极简内存 repository（二选一；优先 SQLite 便于观察真实 Tool 副作用）
- JUnit 5

## Demo 目标

用户输入：

```text
创建一个“学习 Spring AI Advisor”的任务，然后查询这个任务并告诉我任务编号和状态。
```

模型应自主调用：

```text
createTask(...)
getTask(...)
```

业务代码不得通过字符串判断用户意图。

## 实现要求

### 1. Domain

```text
Task
TaskStatus
TaskService
TaskRepository
```

保持普通 Java/Spring 业务代码，不依赖 AI 对象。

### 2. Tools

暴露最少两个 Tool：

```text
createTask
getTask
```

名称、描述、参数必须清晰。

### 3. ChatClient / Model

使用当前官方推荐方式配置。

### 4. Tool Loop

不要自己重新写第一阶段的 `while` Agent loop。

必须使用 Spring AI 当前正式 Tool Calling 机制，让框架执行多轮 tool loop。

### 5. 可观察输出

至少能看到：

```text
User input
Tool name
Tool args
Tool result
Final answer
```

不要要求模型输出 chain-of-thought。

### 6. 测试

至少：

- TaskService 确定性测试；
- Tool 本身测试；
- 一个不依赖真实模型的边界/配置测试；
- README 提供真实模型 Demo 运行方式。

## 本章刻意不要实现

```text
Long-term Memory
RAG
MCP
Planning
Multi-Agent
Checkpoint
```

## README 强制包含

### Framework Mapping

特别回答：

```text
手写 AgentRunner 的 while 去哪了？
ToolRegistry/ToolExecutor 哪些职责消失？
Tool Design 哪些职责仍然存在？
框架如何继续下一轮模型调用？
如何避免无限 Tool Loop？
```

## 验收

运行 Demo 后给出一份实际调用序列，例如：

```text
model turn 1
→ createTask
→ tool result
model turn 2
→ getTask
→ tool result
model turn 3
→ final answer
```
