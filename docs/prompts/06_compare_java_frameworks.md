# 实现 Prompt 06 — Spring AI vs LangChain4j 对照分析

## 本章

```text
docs/chapters/06_SpringAI_vs_LangChain4j.md
```

## 必须读取

```text
AGENTS.md
docs/04_Framework_Mapping规范.md
docs/comparisons/framework_scorecard_template.md
references/handwritten-agent-v1/stateful-agent
spring-ai/stateful-agent
langchain4j/stateful-agent
```

## 目标

不写新业务功能。基于两个真实实现，完成 Java Agent Framework 横向比较。

## 输出

创建：

```text
docs/comparisons/java_framework_comparison.md
```

## 分析维度

至少包含：

```text
Model abstraction
Chat/API style
Tool definition
Tool calling loop
Structured output
Conversation / ChatMemory
Long-term Memory integration
RAG extension point
State / Planning
Observability
Testing
Spring coupling
Framework intrusion
Code volume
Debug path
Extensibility
Version/API stability
```

## 必须做的客观统计

对两个项目分别统计：

- Java source file 数；
- framework-specific source file 数；
- Tool 相关类数；
- Agent infrastructure 类数；
- 主要依赖；
- README Mapping 表中的“框架替代程度”。

LOC 可以统计，但不要把 LOC 当唯一指标。

## 同场景比较

至少选择 3 个相同用户输入，记录：

- 预期 Tool 调用；
- 实际 Tool 调用；
- 最终结果；
- 调试观察方式；
- 如果失败，定位问题从哪里开始。

真实模型存在随机性；不要为了“完全一致”硬编码。

## 最终结论格式

不要写“框架 A 全面更好”。

按场景写：

```text
如果已有大型 Spring Boot 系统...
如果纯 Java 且偏声明式接口...
如果需要深度 Spring Observability...
如果团队想降低 Spring 耦合...
```

最后填写一份 `framework_scorecard_template` 的副本到报告中。
