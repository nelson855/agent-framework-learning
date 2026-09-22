# spring-ai/stateful-agent — 用 Spring AI 重做 Stateful Agent

> 对应教材第 3 章。手写版对照：`references/handwritten-agent-v1/stateful-agent`（stage02）。
> 完整差异对比：[docs/comparisons/ch03_spring_ai_stateful.md](../../docs/comparisons/ch03_spring_ai_stateful.md)。

## Architecture

```
com.example.springai.stateful
├── StatefulAgentApplication      启动入口，只负责启动
├── config/
│   ├── ModelConfig               读 .env（baseUrl / apiKey / model / timeout）
│   └── ChatModelConfig           手工装配 ChatModel / ChatMemory / 各仓库与服务
├── domain/                       纯业务，框架不管（无 @FW-CMP）
│   ├── Task / TaskStatus / TaskRepository / TaskService
│   ├── Conversation / Message / MessageRepository  （UI History 落库）
│   ├── Memory / MemoryRepository                   （长期记忆落库）
│   └── Plan / PlanStep / PlanStepStatus / PlanRepository
│   └── AgentRun / RunStatus / AgentRunRepository    （运行状态落库）
├── agent/                        框架接管层（@FW-CMP 集中在这里）
│   ├── TaskAgentService          一次对话编排：记流水 → 调模型 → 记流水
│   ├── TaskTools                 3 个 @Tool：建任务 / 查任务 / 改状态
│   ├── MemoryService             长期记忆检索（LIKE）
│   ├── MemoryInjectionAdvisor    自定义顾问：把记忆塞进 Prompt
│   ├── Planner / Replanner       结构化输出拆计划 / 出新计划
│   └── PlanExecutor              外层计划循环（自建）
└── web/                          薄控制器
    ├── ConversationController    POST /conversations、GET /conversations/{id}/messages
    ├── ChatController            POST /conversations/{id}/chat（同步跑完整个计划）
    ├── StateController           GET /runs/{runId}（运行状态 + 计划步骤）
    └── dto/                      6 个 records
```

调用顺序（一次对话）：`ChatController` → `PlanExecutor.execute` → `Planner.draftSteps`
→ `PlanRepository.createPlan` → 每步 `TaskAgentService.chat`
（框架内循环调 `TaskTools`）→ 失败则 `Replanner.replan` 出新计划 → 状态落库。

## Version Notes

- Spring Boot **4.0.0**，Spring AI **2.0.0**，Java 21。
- 没有用任何 `spring-ai-starter-*`（本地仓库没有），`ChatModel` 在 `ChatModelConfig`
  里手工 `@Bean` 装配，写法复用 Ch02 的 `OpenAiChatModel` builder。
- **为什么是 Boot 4.0.0 而不是 3.5.x**：Spring AI 2.0 的 JSON 解析模块用到了
  Spring Framework 7 才有的类（`org.springframework.core.Nullness`），Boot 3.5 带的
  Framework 6.2 会直接报 `NoClassDefFoundError`。Stage A 没爆只是因为没走到那段代码，
  Planner 一用 `BeanOutputConverter` 就现形。
- 与 Ch02 的差异：Ch02 是无状态单轮对话；本章多了计划、状态、长期记忆、REST 接口。

## How to run

只跑测试（离线，不连模型、不读 `.env`）：

```bash
mvn -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository test
```

启动应用（需要先在仓库根目录配好 `.env`，应用由你自行启动）：

```bash
mvn -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository spring-boot:run
```

接口示例：

```bash
# 建会话
curl -X POST localhost:8080/conversations \
  -H 'Content-Type: application/json' -d '{"userId":"u1"}'
# 发目标（同步跑完整个计划，返回运行编号）
curl -X POST localhost:8080/conversations/{id}/chat \
  -H 'Content-Type: application/json' -d '{"userId":"u1","goal":"整理本周任务"}'
# 查运行状态
curl localhost:8080/runs/{runId}
# 查消息流水
curl localhost:8080/conversations/{id}/messages
```

## Database schema

SQLite（`data/spring-ai-stateful.db`，5 张表；测试用 `:memory:`）：

```sql
CREATE TABLE IF NOT EXISTS conversation (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id TEXT NOT NULL,
    role TEXT NOT NULL,           -- 'user' | 'assistant'
    content TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES conversation(id)
);

CREATE TABLE IF NOT EXISTS task (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    status TEXT NOT NULL          -- 'OPEN' | 'DONE'
);

CREATE TABLE IF NOT EXISTS memory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    memory_key TEXT NOT NULL,
    memory_value TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_run (
    run_id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    goal TEXT NOT NULL,
    status TEXT NOT NULL,         -- 'RUNNING' | 'DONE' | 'FAILED'
    current_step INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS plan (
    plan_id TEXT PRIMARY KEY,
    run_id TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS plan_step (
    plan_id TEXT NOT NULL,
    step_id TEXT NOT NULL,
    seq INTEGER NOT NULL,
    description TEXT NOT NULL,
    status TEXT NOT NULL,         -- 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
    failure_reason TEXT,
    PRIMARY KEY (plan_id, step_id),
    FOREIGN KEY (plan_id) REFERENCES plan(plan_id)
);
```

## Framework Mapping

| 手写版（stage02） | 框架版（本模块） | 关系 |
|---|---|---|
| `StatefulAgentRunner.executeStep` 内层 ReAct 循环 | `TaskAgentService.chat` 一次 `.call()` | 框架接管 |
| `AgentContext` + `buildContext` | `MessageChatMemoryAdvisor` + `MemoryInjectionAdvisor` | 框架接管组装，来源自建 |
| `Tool` + `ToolDefinition` + `ToolRegistry` | `TaskTools`（`@Tool` 注解） | 框架接管 |
| `AgentDecisionParser` | 无（模型原生 tool_calls） | 整个删掉 |
| `PlanParser` 手写 JSON 解析 | `Planner` 用 `BeanOutputConverter` | 框架辅助 |
| `StatefulAgentRunner.run` 外层循环 | `PlanExecutor.execute` | 自建（框架没这个概念） |
| `plan` / `plan_step` / `agent_run` 表 + 推进逻辑 | 同样的表 + 推进逻辑 | 原样自建 |
| `MemoryRetriever` + 上下文拼接 | `MemoryService` + `MemoryInjectionAdvisor` | 检索自建，注入走顾问 |
| `AgentApplicationService.chat` 7 步编排 | `ChatController` + `PlanExecutor` 分摊 | 拆开自建 |
| `System.out` 进度打印 | `StateController` 查表接口 | 自建 |
| `MemoryExtractor`（从输入提取偏好入库） | 无 | 本章没做（见"刻意没有实现"） |

## Framework Mapping 必答 6 问

1. **Agent Loop 谁负责？** 外层 Plan 循环自建（`PlanExecutor`）；内层 Tool Loop 框架负责（`ToolCallingAdvisor`，一次 `.call()`）。
2. **Message history 谁负责？** 界面看到的历史由 `MessageRepository` 落库（业务负责）；模型上下文由 `ChatMemory` 维护（框架负责，内存实现）。
3. **ChatMemory 与数据库 message/history 的关系？** 两份并存、职责分离：`message` 表是流水账（持久化、审计、界面展示），`ChatMemory` 是窗口（只给模型看，重启可丢）。
4. **Long-term Memory 为什么还需要自建？** 框架的 `ChatMemory` 是"对话窗口"语义，只管最近 N 条；"跨会话偏好"（用户喜欢早上学习）框架不管，存取查库和注入全是业务自己的。
5. **Advisor 承担了哪些原来 ContextBuilder/Harness 的职责？** `MessageChatMemoryAdvisor` 接管"把历史消息拼进提示词"；`MemoryInjectionAdvisor` 接管"把检索到的长期记忆拼进提示词"。原来 `buildContext` 里手动 add 的两块，一块归一个顾问。
6. **哪些业务对象完全不应该知道 Spring AI 存在？** `Task` / `TaskRepository` / `TaskService` / `Conversation` / `Message` / `MessageRepository` / `Memory` / `MemoryRepository` / `Plan` / `PlanStep` / `PlanRepository` / `AgentRun` / `AgentRunRepository`——全部不知道。`import org.springframework.ai` 只出现在 `agent/`（顾问、工具、服务）、`config/`、`web/`。

## Baseline Comparison

| 维度 | 手写版（stage02） | 框架版（本模块） |
|---|---|---|
| 主代码类数量 | 50 | 35 |
| 基础设施类（循环/解析/分派/上下文） | `StatefulAgentRunner`、`AgentDecisionParser`、`ToolRegistry`、`AgentContext`、`PlanParser` 等约 10 个 | `TaskAgentService`、`PlanExecutor`、`MemoryInjectionAdvisor` 3 个 |
| 内层循环代码量 | `executeStep` 约 30 行 + 解析器 51 行 | 1 次 `.call()` |
| 外层循环代码量 | `run()` 约 70 行 | `execute()` 约 30 行（骨架几乎一样） |
| 关键消失类 | — | `AgentDecisionParser`（无文本可抠）、`AgentContext`（被顾问链取代）、`PlanParser`（被转换器取代） |
| 关键保留类 | — | 三张状态表 + 仓库、`TaskService` 业务规则、`MemoryRepository` |

## Known framework limitations

- `ChatMemory` 默认内存实现，重启丢窗口内历史（`message` 表流水不受影响）。
- 外层 Plan 循环、重规划策略（几次算完）、进度查询，框架都没有概念，全自建。
- 工具参数非法时的错误信息不如手写 `ArgumentValidator` 可控。
- `ChatController` 是同步语义（跑完整个计划才返回），多人用要改成异步。

## 本章刻意没有实现什么

- Web UI：只有 REST 接口，`src/main/resources/static/` 留空。
- 记忆提取（手写版 `MemoryExtractor` 从输入里发现偏好入库）：本章记忆靠测试和接口预置，只做了检索和注入。
- 检查点/回放（checkpoint）、人工确认（HITL）、RAG、MCP、多智能体：留给后续章节。
