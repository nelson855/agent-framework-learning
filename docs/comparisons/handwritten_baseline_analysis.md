# Handwritten Baseline 分析报告（Prompt 01）

> 对应 Prompt：`docs/prompts/01_analyze_handwritten_agent.md`
> 对应章节：`docs/chapters/01_从手写Agent到Framework.md`
> 分析对象：`references/handwritten-agent-v1/stateful-agent`、`references/handwritten-agent-v1/long-running-agent`
> 说明：本报告为只读分析产物，未修改 `references/` 下任何文件，未引入任何框架依赖。

---

## 1. Stateful Agent 架构图

```text
用户输入
   │
   ▼
AgentApplicationService.chat()              ← 应用层唯一编排入口
 ├─ 1) MessageRepository.append             → Conversation History 落库（message 表）
 ├─ 2) MemoryExtractor + MemoryRepository   → Long-term Memory 写前门控与保存
 ├─ 3) Planner.createPlan                   → 生成 Plan（plan + plan_step 表）
 ├─ 4) StatefulAgentRunner.run              → Agent Loop + Plan 逐步执行
 │     外层：while 按 Plan 步骤推进，失败调 Replanner（上限 2 次）
 │     内层：每步一个 ReAct 小循环（模型决策 → 工具 → Observation → 最终回答）
 └─ 5) MemoryRetriever.retrieve              → 本轮检索记忆（展示用，另有一份进 Prompt）
   │
   ▼
ChatResult（answer / status / plan / memory / messages 聚合展示）

横向支撑：
- LlmClient（OpenAiCompatibleLlmClient / FakeLlmClient）：模型调用边界
- ToolRegistry + TaskTools + Calculator：工具定义、分派、执行
- Database + 各 *Repository：SQLite 持久化（conversation / message / agent_run / plan / memory / task）
- AppComponents：唯一装配点；Main（CLI）与 WebMain（Web 调试台）复用它
```

## 2. Stateful Agent 类职责表

| 类 | 分层 | 机制 | 一句话职责 |
|---|---|---|---|
| AgentApplicationService | Application | Conversation + 编排 | `chat()` 串起落库 → Memory 提取 → 规划 → Runner → 回读组装 `ChatResult` |
| ConversationService | Application | Conversation | 只管会话外壳与消息流水账，不碰 run / memory / plan |
| Planner | Application | Plan | 初次规划：目标转纯 JSON 计划 |
| Replanner | Application | Plan | 失败重规划：原目标 + 全步骤状态 + 失败原因 → 修订计划 |
| MemoryExtractor | Application | Memory | 写前门控：判断用户消息值不值得长期保存 |
| MemoryRetriever | Application | Memory | 读路径：分词 → LIKE → 按重要度取 TopN 并更新使用时间 |
| StatefulAgentRunner | Agent Infrastructure | Agent loop + Context construction | 外层按 Plan 推进 + 失败 Replan，内层每步 ReAct 小循环，全程持久化 |
| AgentContext | Agent Infrastructure | Context construction + Conversation | 内存增长式消息容器，下轮回填模型 |
| AgentDecisionParser | Agent Infrastructure | Agent loop | 模型 JSON 解析为决策，失败退化为原文 final |
| PlanParser | Agent Infrastructure | Plan | 严格校验计划 JSON，无步骤或空计划抛错 |
| MemoryDecisionParser | Agent Infrastructure | Memory | 解析 Extractor JSON，失败按不保存吞掉 |
| ToolRegistry | Agent Infrastructure | Tool 定义/分派/执行 | 唯一分派入口：查表 → 校验 → 执行，并拼工具说明进系统提示 |
| TaskTools | Agent Infrastructure | Tool 定义 | 默认工具集工厂，组装工具进 Registry |
| Tool / ToolDefinition / ToolCall / ToolResult | Agent Infrastructure / Domain | Tool 定义 | 说明书、调用请求、执行结果的值对象与执行器 |
| Calculator / ArgumentValidator | Agent Infrastructure | Tool 执行 | 四则运算器与执行前确定性参数校验 |
| LlmClient / OpenAiCompatibleLlmClient / FakeLlmClient | Agent Infrastructure | Model client | 模型调用接口、线上实现、离线剧本替身 |
| AgentDecision / AgentDecisionType | Domain | Agent loop | 单步决策值对象，仅 TOOL_CALL / FINAL 两种 |
| Plan / PlanStep / PlanStepStatus | Domain | Plan | 计划、单步、步骤状态（PENDING/RUNNING/DONE/FAILED/SKIPPED） |
| Memory / MemoryDecision | Domain | Memory | 长期记忆行对象与是否保存的判定值对象 |
| Message / StoredMessage / Role / Conversation | Domain | Conversation | 内存消息、落库消息行、角色、会话外壳 |
| AgentRun / RunStatus / RunResult / ChatResult | Domain | Agent state / Conversation | 运行状态行、状态机、单次运行产出、单次对话展示聚合 |
| Task | Domain | Tool 后端 | task 表行映射 |
| Database | Persistence | Repository | 共享 SQLite 连接与建表 |
| MessageRepository / AgentRunRepository / PlanRepository / MemoryRepository / ConversationRepository | Persistence | Repository | 各表存取 |
| TaskStore | Persistence | Repository + Tool 后端 | task 表 CRUD |
| AppComponents | Adapter-UI | 装配 | 唯一装配点，被 CLI / Web 复用 |
| Main | Adapter-UI | CLI | 命令行入口，交互式与 `--demo` 演示 |
| WebMain | Adapter-UI | Web | 内嵌 HTTP 服务 + 静态页面，薄转换层 |
| EnvFile | Adapter-UI | 模型配置 | `.env` 解析，环境变量优先 |

## 3. Long-running Agent 架构图

```text
RunService（应用层唯一编排入口）
 ├─ createRun：建运行 + 存初始检查点 v0
 ├─ stepRun：读最新检查点 → LongRunningRunner.step → 成功存新版本 / 中断标 INTERRUPTED
 ├─ requestInterrupt：设中断目标下标（CrashPolicy 绑定）
 ├─ resumeRun：循环调 stepRun 直到 COMPLETED（上限 100 步），天然跳过 DONE 步骤
 └─ evaluateRun：生成 → Validator 确定性校验 → LlmEvaluator 语义打分，最多 3 轮

LongRunningRunner.step（每次跑一步）：
 判中断 → 组上下文（知识 + 记忆 + ContextBuilder 四块 + 写快照）
        → StepExecutor 执行（echo，保证可复现）
        → Compactor 判压缩 → 存新版本检查点（只 INSERT，不覆盖）

横向支撑：
- Planner：写死 6 步 S1-S6，不调模型
- KnowledgeImporter / Retriever / Repository：3 份内嵌规范，关键词 LIKE 检索
- MemoryRetriever / Repository：记忆 LIKE + 重要度排序 + touch
- CompactionSummarizer：调模型把步骤结果压成结构化摘要，失败走兜底
- ProgramValidator：JSON 结构确定性校验；LlmEvaluator：完整性 2 分 + 可执行性 2 分，≥3 通过
- Database：SQLite 七张表（run / agent_checkpoint / context_snapshot / compaction_summary / evaluation / knowledge_doc / memory）
- Main：CLI 演示序列；WebMain：Web 调试台，只做转换
```

## 4. Long-running Agent 类职责表

| 类 | 分层 | 机制 | 一句话职责 |
|---|---|---|---|
| RunService | Application | Run 编排 + 中断 + 恢复 + 评估 | 唯一编排入口：创建、单步、中断捕获、循环恢复、校验评估重试 |
| KnowledgeImporter | Application | RAG | 幂等写入 3 份内嵌规范文档 |
| Planner | Application | Run 编排 | 确定性生成 6 步计划，不调模型 |
| LongRunningRunner | Agent Infrastructure | 单步编排 + Checkpoint + Trace + Context | 每次跑一步：判中断、组上下文、调工具、压缩、存新版本检查点 |
| StepExecutor | Agent Infrastructure | Run 编排 | 只实现 echo 工具，原样返回参数，保证可复现 |
| ContextBuilder | Agent Infrastructure | Context construction + Trace | 组四块文本并写快照：计划状态、结果或摘要、记忆、知识 |
| Compactor | Agent Infrastructure | Compaction | 超阈值且未压缩过则调摘要器存库，原结果不删 |
| CompactionSummarizer | Agent Infrastructure | Compaction | 调模型把步骤结果压成结构化 JSON，解析失败走兜底 |
| ProgramValidator | Agent Infrastructure | Validator | 确定性校验，不调模型：JSON 可解析、必填字段、类型、非空数组 |
| LlmEvaluator | Agent Infrastructure | Evaluator | 语义打分：完整性 2 分 + 可执行性 2 分，≥3 通过 |
| FinalReportGenerator | Agent Infrastructure | Evaluator + Retry | 调模型生成交付 JSON 原文，合法性交给后两层 |
| KnowledgeRetriever / Tokens | Agent Infrastructure | RAG | 分词取前 3 篇 |
| MemoryRetriever | Agent Infrastructure | Memory | 查记忆并更新使用时间，限 3 条 |
| CrashPolicy / SimulatedCrashException | Agent Infrastructure | Interrupt | 函数式中断策略与受控中断异常（非真崩溃，可捕获） |
| LlmClient / ScriptedLlmClient / OpenAiCompatibleLlmClient | Agent Infrastructure | 模型调用 | 接口、确定性剧本模型、线上实现三分离 |
| EnvFile | Agent Infrastructure | 配置 | `.env` 兜底读配置，环境变量优先 |
| AgentState | Domain | Checkpoint + Resume | 可恢复状态，`nextPendingStepIndex()` 决定跳过 |
| PlanStep / StepStatus / Run / RunStatus / StepOutcome | Domain | Run 编排 | 单步、步骤状态、运行元信息、运行状态、单步回执 |
| VersionedCheckpoint | Domain | Checkpoint + Trace | 检查点行映射：版本、时间、当前步、状态 JSON |
| ContextSnapshot / ContextPolicy | Domain | Context / Compaction | 上下文快照与压缩阈值（默认 100 字符） |
| CompactionSummary | Domain | Compaction | 压缩产物结构 |
| FinalReport / EvaluatorFeedback | Domain | Validator + Evaluator | 交付结构与评估反馈 |
| KnowledgeDoc / Memory | Domain | RAG / Memory | 知识文档与长期记忆 |
| Message / Role / LlmResponse | Domain | Context | 消息三件套 |
| CheckpointRepository | Persistence | Checkpoint + Resume + Trace | `MAX(version)+1` 插新行，读最新与时间线 |
| RunRepository | Persistence | Run 编排 + Trace | run 表创建与状态更新 |
| ContextSnapshotRepository | Persistence | Context + Trace | 快照存取，供调试台查看 |
| CompactionSummaryRepository | Persistence | Compaction | 摘要版本化存取 |
| EvaluationRepository | Persistence | Validator + Evaluator + Retry + Trace | 每轮评估一行，记录校验与打分 |
| KnowledgeRepository / MemoryRepository | Persistence | RAG / Memory | LIKE 检索 |
| Database | Persistence | 全部机制底座 | SQLite 建七张表 |
| Main | Adapter-UI | 演示 | 命令行演示序列：建运行、走 2 步、中断、恢复、评估 |
| WebMain | Adapter-UI | Web + 中断 + Trace | 内嵌 HTTP 调试台，只做转换，动作透传 RunService |

## 5. Agent Loop 实际代码位置

### Stateful Agent：`StatefulAgentRunner.run()`（已抽查确认）

双层循环：

- **外层 Plan 循环**（`run` 方法内 `while (idx < plan.steps().size())`）：DONE / SKIPPED 快跳；完成标 DONE 并持久化；走完标 COMPLETED，落库 assistant 并返回。
- **内层单步 ReAct 循环**（`executeStep` 内 `for (i < MAX_STEPS_PER_STEP=6)`）：调模型 → 解析决策；FINAL 即完成；否则标 WAITING_TOOL → 执行工具 → 回填 assistant + observation；单次失败提前返回；6 步无 final 判失败。
- **Replan 唯一触发点**：某步失败标 FAILED 并持久化失败原因；重规划次数用尽（上限 2）则剩余 PENDING 转 SKIPPED 并整体 FAILED 返回，否则重规划后从新计划头部重扫（已完成步骤自然跳过）。

### Long-running Agent：`RunService` + `LongRunningRunner.step()`

- 不是 ReAct 循环，而是**显式单步模型**：每次只跑一步，状态全进检查点。
- `stepRun` 每次从最新检查点读状态再跑一步；`resumeRun` 循环调 `stepRun` 直到 COMPLETED（上限 100 步）。
- 恢复跳过已完成步骤靠 `AgentState.nextPendingStepIndex()` 找首个非 DONE，下标天然前移。
- 步骤执行层无重试；重试闭环只在评估层（最多 3 轮）。

## 6. Context 组装位置

### Stateful Agent：`StatefulAgentRunner.buildContext()` + `AgentContext`

系统提示（含工具说明）→ 检索到的记忆（`[RETRIEVED MEMORY]`）→ 历史 user / assistant 重放（含本轮刚落库消息；Observation 不入库只在内存）→ 每步注入当前计划步骤。Planner / Replanner / Extractor 用隔离短上下文，不拼大 Context。

注意：`MemoryRetriever.retrieve` 被调两次，一次进 Prompt，一次仅为展示装入 `ChatResult`，不进模型。

### Long-running Agent：`LongRunningRunner.step()` + `ContextBuilder.build()`

每步执行前组四块：计划状态（DONE / RUNNING / PENDING 标记）、步骤结果或压缩摘要（已压缩则用摘要替换明细）、检索记忆、检索知识（标题 + 前 300 字）。随后写 `context_snapshot` 留痕。压缩在工具执行后由 `Compactor.maybeCompact()` 判定，超阈值（默认 100 字符）且未压缩过则调模型压成结构化摘要，原结果保留。

## 7. Memory / State / History 边界

| 概念 | 存哪 | 管什么 | 回答的问题 |
|---|---|---|---|
| Conversation History | `message` 表 | 全量对话流水账（user / assistant 原文；Observation 不入库） | 这个会话聊过什么 |
| Agent State | `agent_run` + `plan` / `plan_step` 表（Stage02）；`run` + `agent_checkpoint` 表（Stage03） | 本次任务干到哪了（状态机、当前步、各步 DONE / FAILED、版本化检查点） | 任务执行到哪了，能否续跑 |
| Long-term Memory | `memory` 表 | 跨会话精选的偏好、事实、约定，经门控写入、按需读出 | 用户长期是什么样 |

关键区别：一条偏好不属于任何一次会话；一次执行不止用户说的话，而是程序内部控制信息；只存消息列表，表达不了跑到第几步、哪步失败、有哪些长期偏好。

## 8. Checkpoint / Resume 机制（Stage03）

- **何时存**：创建运行存初始 v0；每成功执行一步存一次；压缩先发生再存，所以检查点里已带压缩标记；中断抛异常前不存。
- **存什么**：整个 `AgentState` 的 JSON（目标、计划含每步 DONE 与结果、步骤结果集、压缩标记）。刻意不存渲染好的上下文文本，下次由 `ContextBuilder` 现组。
- **如何不覆盖历史**：只 INSERT 新行，版本号取 `MAX(version)+1`；读最新按版本倒序取一条，时间线按版本升序全保留。
- **恢复如何跳过**：每次从最新检查点拿状态，找首个非 DONE 步骤接着跑，已完成步骤天然跳过。

## 9. 未来框架映射候选表

| 手写能力 | 手写位置 | 未来框架对应方向 | 替代程度 | 仍需自己负责 |
|---|---|---|---|---|
| 模型调用 | LlmClient / OpenAiCompatibleLlmClient | 框架 ChatModel / Model Client | 高 | 模型选型、密钥、超时、重试预算 |
| Tool 定义/分派/执行 | Tool / ToolDefinition / ToolRegistry / TaskTools | 框架 Tool / Function tools / 回调 | 高 | 工具语义、参数校验、权限边界、失败语义 |
| Agent Loop | StatefulAgentRunner 双层循环 | 框架 Runner / Agent Executor / Tool Loop | 高 | 最大步数、死循环解释、中间消息是否落库 |
| 决策解析 | AgentDecisionParser | 框架结构化输出 / FunctionCall | 高 | 非法输出的兜底策略 |
| 上下文容器 | AgentContext / ContextBuilder | 框架 ChatMemory / Middleware / Retrieval Augmentor | 高 | 何时注入了什么、快照留痕 |
| 检查点 | CheckpointRepository / VersionedCheckpoint | 框架 Checkpointer / Persistence / Durable execution | 高 | 版本语义、恢复验收 |
| 上下文压缩 | Compactor / CompactionSummarizer | 框架压缩 / 摘要中间件 | 高 | 压缩阈值、摘要丢失什么信息 |
| 记忆读写 | MemoryExtractor / MemoryRetriever | 框架 Memory / Advisor | 中 | 什么值得长期记、检索词与排序 |
| 规划/重规划 | Planner / Replanner / PlanParser | 框架 Planner / Workflow state | 中 | 目标拆分粒度、重规划上限 |
| RAG 检索 | KnowledgeRetriever / Tokens | 框架向量检索 / Retrieval Augmentor | 中（当前只是 LIKE，占位性质） | 文档质量、分块、召回标准 |
| 确定性校验 | ProgramValidator / ArgumentValidator | 框架 Guardrail / 校验钩子 | 中 | 字段规则本身是业务定的 |
| 语义评估 | LlmEvaluator / FinalReportGenerator | 框架评估 / 裁判模块 | 中 | 量表、通过线、失败反馈怎么写 |
| 编排顺序 | AgentApplicationService / RunService | 框架 Runner + 工作流 | 中 | 整条业务顺序仍是应用代码 |
| 业务工具实现 | Calculator / StepExecutor / TaskStore | 无（业务代码） | 低 | 全部自己负责 |
| 交付结构 | FinalReport / Plan / Task | 无（领域模型） | 低 | 全部自己负责 |
| 装配与入口 | AppComponents / Main / WebMain | 框架 Boot 集成 | 低 | 调试台展示什么、端口、启动方式 |
| 测试替身 | FakeLlmClient / ScriptedLlmClient | 框架测试替身概念 | 低到中 | 剧本逻辑仍需手写 |

## 10. 验收问题回答

### 哪 5 类代码最可能被成熟框架显著减少？

1. 模型调用封装（`OpenAiCompatibleLlmClient` → 框架 ChatModel）。
2. 工具定义、分派、执行 glue code（`ToolRegistry` / `Tool` / `ToolDefinition` → 框架 Tool 抽象）。
3. Agent 循环本身（`StatefulAgentRunner` 双层循环、最大步数、决策解析 → 框架 Runner / Tool Loop）。
4. 上下文容器与组装（`AgentContext` / `ContextBuilder` → 框架 ChatMemory / 中间件）。
5. 检查点存取与版本时间线（`CheckpointRepository` / `VersionedCheckpoint` → 框架 Checkpointer / Durable execution）。

### 哪 5 类责任即使使用框架仍必须由项目负责？

1. 业务工具的真实语义与副作用（算对算错、任务增删改、权限边界）。
2. 什么值得长期记忆、记忆分级与遗忘策略（门控规则、重要度）。
3. 目标拆分粒度与重规划上限（计划几步、失败几次算完）。
4. 交付结构与确定性校验规则（字段、类型、非空约束）。
5. 评估量表、通过线与失败反馈（什么算好、几分放行、打回重写什么）。

### 哪些复杂度看起来容易被框架隐藏而不是消失？

- Tool Loop 被接管后，工具描述设计、权限控制、失败语义并没有消失，只是看不见了，出问题时仍要懂循环才能定位死循环、重复调用、步数耗尽。
- 上下文自动组装后，何时注入了记忆、知识、压缩摘要变得不透明，不留快照就解释不了模型为什么这样答。
- 检查点自动存后，版本语义与恢复正确性（跳过什么、重做什么）仍需验收，否则中断恢复可能静默跑偏。
- 结构化输出自动解析后，非法输出的兜底策略仍是业务责任，解析失败吞掉还是打回，结果完全不同。
- 评估自动打分后，量表松紧直接决定放行质量，分数虚高比直接报错更难发现。
