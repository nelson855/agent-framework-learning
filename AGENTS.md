# AGENTS.md — Agent Framework Learning Repository

## 1. 仓库性质

这是一个 **AI Agent 框架教学与对照实验仓库**，不是生产系统。

本仓库的首要目标不是代码复用率、架构复杂度或生产级完备性，而是帮助开发者理解：

- Agent Framework 的抽象边界；
- Framework / Runtime / Harness 的差别；
- 不同框架如何映射到已经掌握的 Agent 基础机制；
- 在什么场景下应该选择什么框架。

优先级：

1. 框架机制可观察；
2. 能与手写版本对照；
3. 可运行、可测试；
4. 使用框架的官方推荐方式；
5. 代码整洁；
6. 工程完备性。

## 2. 开始任何任务前必须阅读

至少读取：

- 本文件；
- 当前 Prompt 指定的教材章节；
- `docs/04_Framework_Mapping规范.md`；
- 如任务涉及 Benchmark，则读取对应 `references/handwritten-agent-v1/` 基准项目。

## 3. references/ 是只读区

`references/handwritten-agent-v1/` 保存第一阶段手写 Agent 的冻结快照。

允许：

- 阅读；
- 搜索；
- 运行测试；
- 分析；
- 对比。

禁止：

- 修改；
- 格式化；
- 重构；
- 升级依赖；
- 修复 bug；
- 添加新框架；
- 把新实现写入 references。

如果基准本身有问题，在报告里记录，不要修复基准。

## 4. 技术基线

### Java 模块

默认：

- JDK 21
- Maven
- JUnit 5
- SQLite（需要持久化时）

Java 框架实验优先使用框架自己的官方集成方式。

### Python 模块

仅在教材明确要求时使用，主要用于 LangGraph / OpenAI Agents SDK 等一等生态实验。

要求：

- 使用独立虚拟环境或 `uv`；
- 不污染 Java Maven 模块；
- 保持代码极小；
- 不把 Python 语法教学变成本章重点。

## 5. 版本与 API 规则

框架变化很快。实现 Prompt 中出现的 API 名称是教学线索，不应被视为永久固定的接口契约。

实施时必须：

1. 优先查看对应框架**官方文档**；
2. 确认当前稳定版本和对应 API；
3. Java 依赖优先从官方文档/Maven Central 确认；
4. Python 依赖优先从官方文档/官方包索引确认；
5. 不要因为模型记忆中的旧 API 而强行使用已废弃接口；
6. 如果当前 API 与教材描述不同，在 README 的 `Version Notes` 中解释差异。

禁止为了“更新”而自行换成完全不同的框架或第三方封装。

## 6. 框架实验原则

### 6.1 不重复造轮子

第一阶段为了理解原理，我们手写 Agent Loop、Memory、Checkpoint 等。

第二阶段的目标相反：

> **应该主动使用当前框架提供的正式抽象，然后观察它替代了什么。**

例如学习 Spring AI Tool Calling 时，不要重新写一套完整 Agent Loop 来绕过框架。

### 6.2 但不能隐藏业务责任

框架不能成为“所有逻辑塞进去”的借口。

必须区分：

- Framework Infrastructure；
- Agent Policy / Prompt；
- Business Tool；
- Domain State；
- Persistence；
- Web / CLI Adapter。

### 6.3 同一 Benchmark 保持业务一致

Spring AI 和 LangChain4j 重做 Stateful Agent 时，应尽量保持：

- 相同业务任务；
- 相同工具语义；
- 相同主要状态；
- 相同验收场景。

不要为了展示框架特色随意改变业务，导致无法比较。

### 6.4 不追求生产化

除非章节明确要求，否则不要主动增加：

- Kubernetes
- MQ
- 分布式缓存
- 微服务拆分
- OAuth 全套
- 前端工程化
- CI/CD 大型流水线

## 7. Web UI 原则

Web 页面仍然是“Agent 调试台”，不是产品前端。

若使用 Web：

- 页面必须优先展示 Agent 内部可观察状态；
- 不要花大量代码做 UI；
- Java 模块可以使用 Spring Boot 自带 Web 能力，或框架章节指定方案；
- 不要为了页面额外引入 React/Vue，除非 Prompt 明确要求。

## 8. Framework Mapping 是强制产物

每个框架实现必须在模块 README 中包含：

```markdown
## Framework Mapping

| 本项目中的能力 | 第一阶段手写组件 | 当前框架抽象 | 谁负责运行 | 仍需自己负责什么 |
|---|---|---|---|---|
```

并回答：

- 删除了哪些手写代码？
- 哪些复杂度只是转移到了框架？
- 控制权减少在哪里？
- 调试方式发生了什么变化？
- 如果不用该框架，需要重新实现什么？

## 9. 测试规则

每个模块至少包含：

- 一个不依赖真实 LLM 的确定性测试；
- 一个核心框架集成测试或可运行 Demo；
- README 中写明真实模型运行需要哪些环境变量。

不得要求所有单元测试都联网调用真实 LLM。

## 10. 执行任务后的汇报格式

完成 Prompt 后必须汇报：

1. 创建/修改了哪些文件；
2. 使用了哪个框架版本；
3. 官方 API 与教材描述是否有差异；
4. 测试结果；
5. Demo 观察结果；
6. Framework Mapping 的关键结论；
7. 本章刻意没有实现什么。
