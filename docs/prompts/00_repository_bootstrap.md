# 实现 Prompt 00 — 初始化第二阶段框架学习仓库

## 目标

初始化 `agent-framework-learning` 的本地代码实验环境，并把第一阶段手写 Agent 导入为**只读 Baseline**。

不要实现任何 Spring AI / LangChain4j / LangGraph 业务功能。

## 开始前读取

```text
AGENTS.md
README.md
LOCAL_PATHS.example.md
docs/00_教材总纲.md
docs/03_代码仓库架构说明.md
```

## Step 1：定位旧仓库

按以下顺序查找：

1. 如果根目录存在 `LOCAL_PATHS.md`，读取其中：

```text
HANDWRITTEN_AGENT_REPO=...
```

2. 如果没有，尝试：

```text
../agent-learning
```

3. 如果仍不存在：

- 不要扫描整个磁盘；
- 不要猜路径；
- 不要重新生成旧项目；
- 停止 Baseline 导入并明确告诉用户需要提供第一阶段仓库路径。

## Step 2：验证旧仓库

至少检查：

```text
<OLD>/stages/stage02-stateful-agent
<OLD>/stages/stage03-long-running-agent
```

可选检查：

```text
<OLD>/stages/stage04-agent-harness
```

如果目录命名略有差异，可以只在旧仓库的 `stages/` 目录内做有限搜索并报告实际路径；不要全盘搜索。

## Step 3：复制冻结快照

复制为：

```text
references/handwritten-agent-v1/stateful-agent
references/handwritten-agent-v1/long-running-agent
```

如果 stage04 存在，可选复制：

```text
references/handwritten-agent-v1/agent-harness
```

不要创建 symlink，不使用 Git submodule。

复制完成后不得修改这些文件。

## Step 4：创建实验目录骨架

确保存在：

```text
spring-ai/basic
spring-ai/stateful-agent
spring-ai/advanced
langchain4j/stateful-agent
langgraph/long-running-agent
google-adk/multi-agent
sdk-experiments/openai
sdk-experiments/claude
```

不要提前生成框架代码。

## Step 5：Baseline 检查

对导入的两个必需 Baseline：

- 列出主要文件；
- 如果有 README，读取；
- 如果可运行测试，在不修改代码的情况下运行；
- 失败只记录，不修复。

## Step 6：生成初始化报告

创建：

```text
docs/comparisons/bootstrap_report.md
```

包含：

- 旧仓库实际来源路径；
- 导入了哪些 Baseline；
- 每个 Baseline 的主要入口；
- 测试是否能运行；
- 当前仓库实验目录状态；
- 明确声明 references 已冻结。

## 验收

1. 后续任何 Prompt 都不需要旧仓库绝对路径；
2. `references/handwritten-agent-v1/stateful-agent` 存在；
3. `references/handwritten-agent-v1/long-running-agent` 存在；
4. 未修改教材文件；
5. 未提前引入任何 Agent Framework。
