# 实现 Prompt 04 — Spring AI Advanced：RAG + MCP + Observability

## 本章

```text
docs/chapters/04_SpringAI进阶_RAG_MCP_Observability.md
```

## 先读取

```text
AGENTS.md
docs/04_Framework_Mapping规范.md
docs/05_版本与官方文档策略.md
spring-ai/basic/README.md
spring-ai/stateful-agent/README.md
```

## 目标目录

```text
spring-ai/advanced
```

这是独立小实验，不要复制完整 Stateful Agent。

## 任务场景

本地放一小份 Markdown 知识库，例如任务处理规范：

```text
BLOCKED 任务如何升级处理
DONE 任务如何归档
高优先级任务响应时限
```

用户提问：

```text
“根据规范，告诉我 BLOCKED 任务应该怎么处理；如果我提供了任务号，再查询任务当前状态。”
```

## Part A — RAG

使用 Spring AI 当前正式 RAG/VectorStore/Advisor 方案。

要求：

- 文档 ingest；
- retrieval；
- 将检索内容注入 Context；
- 输出 source metadata 或至少可追踪检索片段。

重点观察：

```text
retrieval 在 Advisor chain 的哪个位置？
```

## Part B — MCP

实现或连接一个**本地教学 MCP Server**，只暴露简单只读 Tool，例如：

```text
get_task_status(task_id)
```

不要依赖第三方生产账户。

Spring AI 模块作为 MCP Client 使用该 Tool。

重点观察：

```text
MCP Tool discovery
schema
remote invocation
error handling
```

## Part C — Observability

使用当前 Spring AI / Spring Boot 官方推荐 observability 方式。

至少能区分一次请求中的：

```text
model call
retrieval
MCP tool call
final response
```

如果需要额外本地观察工具，只选最轻量方案。

## 测试

必须可在不调用真实模型时测试：

- 文档 ingest/retrieval；
- MCP server tool；
- task lookup；
- 至少一个 advisor/component 边界。

## README Framework Mapping

回答：

1. RAG 对应第一阶段哪个 ContextBuilder 流程？
2. MCP 改变了 Tool 的哪一层边界？
3. Observability 与手写 Trace 的差别是什么？
4. 框架是否自动解决了远程 Tool 的权限问题？为什么没有？
