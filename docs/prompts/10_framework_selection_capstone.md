# 实现 Prompt 10 — 框架选型与最终综合报告

## 本章

```text
docs/chapters/10_框架选型与最终综合.md
```

## 必须读取

读取本仓库已经完成的全部：

```text
docs/comparisons/handwritten_baseline_analysis.md
docs/comparisons/java_framework_comparison.md
docs/comparisons/agent_sdk_harness_notes.md
spring-ai/*/README.md
langchain4j/stateful-agent/README.md
langgraph/long-running-agent/README.md
google-adk/multi-agent/README.md
sdk-experiments/openai/README.md
sdk-experiments/claude/README.md
```

缺失文件不要猜，明确标记“实验未完成”。

## 目标

不写新 Agent 业务代码。

生成最终报告：

```text
docs/comparisons/final_framework_report.md
```

并完成：

```text
docs/comparisons/final_selection_matrix.md
```

## 报告结构

### 1. 一页框架地图

区分：

```text
Model SDK
Application Framework
Orchestration Runtime
Agent SDK
Harness / Managed Runtime
```

将本教材所有框架放到图中；允许一个框架跨多个层，但要标主定位。

### 2. Handwritten → Framework 演进

用具体类说明：

```text
哪些代码消失
哪些代码迁移
哪些责任保留
```

### 3. Java Framework 对比

Spring AI vs LangChain4j，引用实际项目证据。

### 4. Runtime 对比

解释为什么 LangGraph 不是简单“另一个 Spring AI”。

### 5. Multi-Agent

解释 deterministic workflow、agent delegation、handoff 的差异。

### 6. SDK / Harness

解释 Runner、Tool Runner、environment、permission、sandbox 等层次。

### 7. 10 道选型题

至少覆盖：

1. Spring Boot 知识库客服；
2. Java 内部业务 Copilot；
3. 纯 Java CLI assistant；
4. 30 分钟 Research Agent；
5. 人工审批合同流程；
6. Gemini Multi-Agent；
7. OpenAI handoff system；
8. Coding Agent；
9. 简单一次性 structured output；
10. 高可控自定义 loop。

每道题输出：

```text
推荐层次
推荐框架/SDK
备选
为什么
什么情况下会改选
```

### 8. 个人默认技术栈

结合本仓库实际体验，给出：

```text
Java 普通 Agent 默认
Java RAG 默认
复杂长任务默认
Multi-Agent 默认
SDK/Harness 研究默认
```

不要只根据“流行度”决定。

### 9. 何时不用框架

列出至少 5 个情况。

## 最终自测

报告最后回答：

> 如果明天出现一个新 Agent 框架，我应该用哪 10 个问题在 30 分钟内判断它处在哪一层、值不值得学？

## 禁止

- 创建一个所谓“统一框架封装层”把所有实现抹平；
- 重新开发业务；
- 用主观印象替代已经完成的代码证据。
