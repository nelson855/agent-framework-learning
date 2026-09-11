# Bootstrap Report（Prompt 00 初始化报告）

> 生成时间：2026-09-11
> 对应 Prompt：`docs/prompts/00_repository_bootstrap.md`
> 本报告为第一阶段学习任务的交付物，只记录初始化结果，不包含任何框架实现。

## 1. 旧仓库实际来源路径

- 本机未发现 `LOCAL_PATHS.md`，按 Prompt 默认规则使用相邻目录：
- `HANDWRITTEN_AGENT_REPO=/Users/nelson/ai-projects/20260812-agent-learning/agent-learning`
- 验证方式：`ls stages/` 确认存在 `stage02-stateful-agent`、`stage03-long-running-agent`、`stage04-agent-harness`。

## 2. 导入了哪些 Baseline

复制方式：`cp README.md + pom.xml + src/`，排除 `target/`、`.claude/`、`.DS_Store`、`*.db` 等运行产物。

| 目标（冻结快照） | 来源 | 文件数（约） | 是否必需 |
|---|---|---|---|
| `references/handwritten-agent-v1/stateful-agent` | `stages/stage02-stateful-agent` | main 约 51 个 Java + 3 个 web 资源，test 2 个 | 是 |
| `references/handwritten-agent-v1/long-running-agent` | `stages/stage03-long-running-agent` | main 约 50 个 Java + 3 个 web 资源，test 6 个 | 是 |
| `references/handwritten-agent-v1/agent-harness` | `stages/stage04-agent-harness` | main 约 40 个 Java + 3 个 web 资源，test 3 个 | 可选，已导入 |

## 3. 每个 Baseline 的主要入口

### 3.1 stateful-agent（Stage02）

- `Main.java`：CLI 入口，支持 `--demo` 离线演示（Session A 存 Maven 偏好 → Session B 检索执行），无真实模型时退化为 `FakeLlmClient`。
- `WebMain.java`：Web 调试台入口（默认 8080，可传参覆盖）。
- `AgentApplicationService.chat()`：核心编排，顺序为 Message 落库 → Memory 提取保存 → Planner 生成计划 → StatefulAgentRunner 执行 → Memory 检索。
- `StatefulAgentRunner.java`：每个计划步骤跑小 ReAct 循环，工具失败调 `Replanner`（有上限）。
- 教学要点：Conversation / Agent State（`agent_run`）/ Plan（`plan`+`plan_step`）/ Long-term Memory（`memory`）四张表语义不同。

### 3.2 long-running-agent（Stage03）

- `Main.java`：CLI Demo（确定性脚本，不依赖真实 LLM），演示 中断 → Resume → Validator 拒绝 → 重试 → Evaluator 通过 的完整闭环。
- `WebMain.java`：Web 调试台入口。
- `RunService.java`：核心编排器，Web 与 CLI 共用；`LongRunningRunner` 负责每步执行 + Checkpoint 落点 + 中断判定。
- `ContextBuilder` / `Compactor` / `CheckpointRepository` / `ProgramValidator` / `LlmEvaluator`：分别对应上下文组装、压缩、版本化 Checkpoint、确定性校验、语义评估。
- 教学要点：Context Selection + Compaction 解决上下文膨胀；版本化 Checkpoint + Resume 解决中断恢复。

### 3.3 agent-harness（Stage04，可选）

- `Main.java`、`HarnessService.java`、`Orchestrator.java`、`AgentRunner.java`、`WorkerAgent.java` 存在，但 `README.md` 仍标注为“教学占位骨架”。
- 本次按 Prompt 可选规则一并冻结导入，供后续 Harness 章节对照。

## 4. 测试是否能运行

测试命令（使用本机 Maven 路径，不修改代码）：

```bash
cd /Users/nelson/ai-projects/20260812-agent-learning/agent-learning
mvn -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository -pl stages/stage02-stateful-agent test
mvn -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository -pl stages/stage03-long-running-agent test
mvn -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository -pl stages/stage04-agent-harness test
```

| Baseline | 在旧仓库原址运行 | 在 `references/` 快照内直接运行 | 结论 |
|---|---|---|---|
| stage02 | 通过（6 个测试，`AgentApplicationServiceTest` 5 + `MainTest` 1） | 不可直接运行：`pom.xml` 的 `parent.relativePath` 指向旧仓库根 `pom.xml`，新位置解析失败 | 快照仅供只读对照，测试以旧仓库原址结果为准 |
| stage03 | 通过（17 个测试全绿） | 同上，父 POM 解析失败 | 同上 |
| stage04 | 失败：`MainTest.java:11` 调用 `Main.greeting()`，但 `Main` 无此方法，编译失败 | 同上，父 POM 解析失败 | 基准本身有问题，只记录不修复（符合 AGENTS.md 只读规则） |

## 5. 当前仓库实验目录状态

已创建（空目录骨架，未提前生成任何框架代码）：

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

- 未添加任何 Spring AI / LangChain4j / LangGraph 依赖；
- 未修改任何 `docs/` 教材文件；
- 本报告为本次唯一新增的 `docs/` 产物，符合 Prompt Step 6 要求。

## 6. references 已冻结声明

**`references/handwritten-agent-v1/` 已冻结，为只读区。**

允许：阅读、搜索、运行测试（原址运行）、分析、对比。

禁止：修改、格式化、重构、升级依赖、修复 bug（含 stage04 的编译失败也不修）、添加新框架、把新实现写入 references。

后续所有 Prompt 只使用当前仓库内部的 `references/handwritten-agent-v1/`，不再依赖外部绝对路径。

## 7. 验收对照

1. 后续 Prompt 不再需要旧仓库绝对路径：通过，本报告记录后即可只用 `references/`；
2. `references/handwritten-agent-v1/stateful-agent` 存在：通过；
3. `references/handwritten-agent-v1/long-running-agent` 存在：通过；
4. 未修改教材文件：通过（仅新增本报告）；
5. 未提前引入任何 Agent Framework：通过（实验目录均为空）。

## 8. 下一步建议

- 你的顺序是对的：下一步按路线进入 `docs/prompts/01_analyze_handwritten_agent.md`（对应 Chapter 01，手写 Baseline 分析，主要做分析不写代码）。
- 然后再按 02 → 03 → … 顺序进入 Spring AI 等框架章节。
