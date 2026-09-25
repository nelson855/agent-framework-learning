# langchain4j/stateful-agent — 用 LangChain4j 重做 Stateful Agent

> 对应教材第 5 章。手写版对照：`references/handwritten-agent-v1/stateful-agent`（stage02）。
> Spring AI 版对照：`spring-ai/stateful-agent`（第 3 章，同业务）。
> 完整差异对比：[docs/comparisons/ch05_langchain4j_stateful.md](../../docs/comparisons/ch05_langchain4j_stateful.md)。

## Architecture

```
com.example.langchain4j.stateful
├── StatefulAgentApp              启动入口，只负责装配和起 Web
├── config/
│   ├── ModelConfig               读 .env（baseUrl / apiKey / model / timeout）
│   └── ChatModelFactory          造 OpenAiChatModel；没配 key 就降级离线模型
├── domain/                       纯业务，框架不管（无 @FW-CMP，与 Spring AI 版表结构一致）
│   ├── Task / TaskStatus / TaskRepository / TaskService
│   ├── Conversation / Message / MessageRepository  （界面历史落库）
│   ├── Memory / MemoryRepository                   （长期记忆落库）
│   └── Plan / PlanStep / PlanStepStatus / PlanRepository
│   └── AgentRun / RunStatus / AgentRunRepository    （运行状态落库）
├── agent/                        框架接管层（@FW-CMP 集中在这里）
│   ├── StatefulAssistant         声明式接口：方法签名即能力，代理负责实现
│   ├── TaskAgentService          装配代理：ChatModel + ChatMemoryProvider + TaskTools
│   ├── TaskTools                 3 个 @Tool：建任务 / 查任务 / 改状态（含调用记录）
│   ├── MemoryService             长期记忆检索（LIKE，与 Spring AI 版一致）
│   ├── Planner / Replanner       底层 ChatModel 直调拆计划 / 出新计划
│   └── PlanExecutor              外层计划循环（自建）
├── web/
│   └── WebServer                 JDK HttpServer：REST 接口 + 静态调试台
├── experiments/
│   └── AgenticApiProbe           实验性 Agentic API 观察记录（只记录结论，不依赖）
└── resources/static/index.html   调试台页面：左 Chat，右 State / Plan / Memory / Tool Calls / Window
```

调用顺序（一次对话）：`WebServer` → `PlanExecutor.execute` → `Planner.draftSteps`
→ `PlanRepository.createPlan` → 每步 `TaskAgentService.chat`
（代理内循环调 `TaskTools`）→ 失败则 `Replanner.replan` 出新计划 → 状态落库。

## Version Notes

- LangChain4j **1.20.0**（稳定 BOM `dev.langchain4j:langchain4j-bom:1.20.0`），Java 21。
- 用到的稳定模块：`langchain4j`（AiServices、MessageWindowChatMemory）、
  `langchain4j-open-ai`（OpenAiChatModel）、`langchain4j-core`（ChatModel 接口）。
- 与教材描述的差异：`ChatMemoryAccess` 接口搬到了
  `dev.langchain4j.service.memory` 包；本模块没用它，而是直接持有
  `ChatMemoryProvider` 的内存表并暴露 `inspectWindow`，
  效果一样，少一层接口，调试台读窗口更直接。
- 编译保留了方法参数名（`maven-compiler-plugin` 的 `parameters=true`），
  否则工具参数在模型眼里会变成 `arg0`，工具说明书就是乱的——
  这是本章真实踩到的坑，详见对比文档。
- **Agentic API 状态**：官方把新一代 Agentic API（含 `langchain4j-agentic` 构件）
  放在 beta 版本线（如 `1.20.0-betaXX`）维护，稳定 BOM 不收录。
  本模块主路径不依赖它，只在 `experiments/AgenticApiProbe` 留观察记录。

## How to run

只跑测试（离线，不连模型、不读 `.env`）：

```bash
mvn -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository test
```

启动应用（应用由你自行启动；没配 `.env` 会以降级离线模式启动，只演示页面和状态流转）：

```bash
mvn -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository compile exec:java \
  -Dexec.mainClass=com.example.langchain4j.stateful.StatefulAgentApp
```

真实模型需要在仓库根目录放 `.env`（键名与 Spring AI 版一致，同一份可复用）：

```text
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-4o-mini
```

接口示例（默认端口 8081，避免与 Spring AI 版的 8080 冲突）：

```bash
# 建会话
curl -X POST localhost:8081/conversations \
  -H 'Content-Type: application/json' -d '{"userId":"u1"}'
# 发目标（同步跑完整个计划，返回运行编号）
curl -X POST localhost:8081/conversations/{id}/chat \
  -H 'Content-Type: application/json' -d '{"userId":"u1","goal":"整理本周任务"}'
# 查运行状态（含计划步骤）
curl localhost:8081/runs/{runId}
# 调试台数据（窗口 / 记忆 / 工具调用）
curl 'localhost:8081/debug/conversations/{id}?userId=u1&q=学习'
```

Web 选型说明：用 JDK 自带 `HttpServer`，没用 Spring Boot。
原因有三：LangChain4j 本身不带 Web 能力，引入 Boot 等于同时学两个框架；
手写版本来就是 `HttpServer`，沿用后"换框架不用换 Web 层"这个结论看得最清楚；
调试台只是内部观察页，不值得为它背一个重型依赖。
代价是路由和参数解析全手写（见 `WebServer` 底部小工具方法）。

## AI Services 解释

`StatefulAssistant` 是一个普通 Java 接口，只有一个方法：

```java
@SystemMessage("你是任务管理助手……")
String chat(@MemoryId String memoryId, @UserMessage String message);
```

`AiServices.builder(...).chatModel(...).chatMemoryProvider(...).tools(...).build()`
返回的代理在这个方法被调用时，依次做五件事：

```text
1. 按 memoryId 找到（或新建）这个会话的 ChatMemory 窗口
2. 把系统提示词 + 窗口历史 + 本次用户消息拼成请求
3. 调 ChatModel；模型返回 tool_calls 就执行工具，再把结果送回模型，直到模型给最终答复
4. 把本轮用户消息和最终答复写回窗口
5. 返回最终答复文本
```

也就是说，一个接口方法背后装了：提示词组装、记忆存取、工具循环、结果回填。
业务代码只剩"调方法"，第 3 步的循环细节（重试几次、并行调不调、超限怎么办）
藏进代理，调参靠 builder 参数而不是改代码。
想看代理实际干了什么：调试台右侧的 Recent Tool Calls（工具方法自己记的账）
和 Window（窗口里模型实际看到的消息）就是证据。

## ChatMemory vs History vs Long-term Memory

| 概念 | 本模块位置 | 存哪 | 重启还在吗 | 管什么 |
|---|---|---|---|---|
| 界面历史 History | `MessageRepository`（message 表） | SQLite | 在 | 完整流水账，给人看、审计用 |
| 对话窗口 ChatMemory | `MessageWindowChatMemory`（每会话一个） | 内存 | 不在 | 最近 20 条，给模型看，超长自动丢旧的 |
| 长期记忆 Long-term Memory | `MemoryRepository`（memory 表）+ `MemoryService` | SQLite | 在 | 跨会话偏好（如"早上学习效率高"），调代理前手工拼前缀 |

不要因为 `ChatMemory` 名字里有 Memory 就把长期记忆塞进去：
窗口是"最近说什么"，长期记忆是"这个人一直是什么样"，两者语义不同、
寿命不同，本模块刻意分开存、分开用。

## Database schema

SQLite（`data/langchain4j-stateful.db`，与 Spring AI 版同表设计；测试用 `:memory:`）：

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
| `StatefulAgentRunner.executeStep` 内层 ReAct 循环 + `AgentDecisionParser` | `StatefulAssistant` 代理一次 `chat()` | 框架接管，解析器整个删掉 |
| `AgentContext` + `buildContext` | 代理内组装（窗口 + 工具结果自动回填）+ 调前手工拼记忆前缀 | 框架接管一半，记忆注入手工做 |
| `Tool` + `ToolDefinition` + `ToolRegistry` | `TaskTools`（`@Tool` + `@P` 注解） | 框架接管 |
| `PlanParser` 手写 JSON 解析 | `Planner` 手工 Jackson 解析（刻意没用框架转换器） | 自建，对标用 |
| `StatefulAgentRunner.run` 外层循环 | `PlanExecutor.execute` | 自建（框架没这个概念） |
| `plan` / `plan_step` / `agent_run` 表 + 推进逻辑 | 同样的表 + 推进逻辑 | 原样自建 |
| `MemoryRetriever` + 上下文拼接 | `MemoryService` + 调前拼前缀 | 检索自建，注入手工做 |
| `AgentApplicationService.chat` 7 步编排 | `WebServer` + `PlanExecutor` 分摊 | 拆开自建 |
| `OpenAiCompatibleLlmClient` 手写 HTTP | `OpenAiChatModel.builder()` | 框架接管 |
| 手写 `WebMain`（JDK HttpServer） | `WebServer`（同样 JDK HttpServer） | 原样沿用 |

### Framework Mapping 必答 5 问

1. **`AiServices` 代理背后负责了什么？**
   提示词组装、按 `memoryId` 存取窗口、工具循环（调 → 回填 → 再调直到收尾）、
   本轮消息写回窗口。见 README"AI Services 解释"一节的五步。
2. **Tool loop 谁负责？**
   内层（单步内"模型→工具→模型"）代理负责；外层 Plan 循环自建（`PlanExecutor`）。
   分界线与 Spring AI 版完全一致。
3. **`ChatMemoryProvider` 对应第一阶段什么概念？**
   对应手写版"每次按 conversationId 查库拼历史"那段逻辑，但语义从"全量账本"
   变成"定长窗口"：每个会话独立窗口，超 20 条自动丢最旧的。持久账本仍是 message 表。
4. **哪些地方比 Spring AI 更声明式？**
   助手入口（接口 + 注解 vs `ChatClient` 链式调用）、工具定义（`@Tool`/`@P`
   vs `@Tool`/`@ToolParam`  nearly 一样，真正更声明式的是系统提示词从服务类字符串
   搬到接口注解上）。代价见下一问。
5. **声明式 API 带来了哪些调试成本？**
   三个：循环藏进代理后，"调了几次工具、每次参数是什么"看不见了，
   本模块靠工具方法自己记账（`recentCalls`）补回来；窗口内容看不见，
   靠 `inspectWindow` 暴露；Planner 刻意走底层直调，就是为了留一条"看得见"的路。
   声明式省的是写法，多的是"看不见中间过程"的心智负担。

## 与 Spring AI 的第一印象对比

| 维度 | Spring AI 版（Ch03） | 本模块（Ch05） |
|---|---|---|
| 助手入口 | `ChatClient` 链式调用 + 顾问链 | 接口 + 注解，代理实现 |
| 工具定义 | `@Tool` + `@ToolParam` | `@Tool` + `@P`，语义一样 |
| 历史记忆 | `MessageChatMemoryAdvisor` + `ChatMemory` | `ChatMemoryProvider` + 窗口 |
| 长期记忆注入 | `MemoryInjectionAdvisor`（顾问链上的一环） | 调代理前手工拼前缀（链外） |
| 结构化输出 | `BeanOutputConverter`（框架生成格式约束） | 提示词一句话 + 手工 Jackson 解析 |
| Web 层 | Spring Boot REST（static 留空） | JDK HttpServer + 单页调试台 |
| 外层计划循环 | 自建 `PlanExecutor` | 同样自建，骨架几乎一样 |

一句话印象：两家接管的是同一层（内层工具循环），都不管计划、状态、长期记忆；
LangChain4j 把入口藏得更深（接口代理），Spring AI 把过程铺得更开（调用链 + 顾问）。
藏得深写得少，但"中间发生了什么"要靠自己记账补回来——本模块的工具调用记录
和窗口检查口就是补回来的那部分。

## Known framework limitations

- `ChatMemory` 默认内存窗口，重启丢窗口内历史（message 表流水不受影响）。
- 外层 Plan 循环、重规划策略、进度查询，框架都没有概念，全自建。
- 代理的工具循环参数（最大步数、并行调用）靠 builder 调，不如手写循环直观。
- 工具参数非法时的错误信息不如手写 `ArgumentValidator` 可控。
- `WebServer` 的 chat 接口是同步语义（跑完整个计划才返回），多人用要改成异步。

## 本章刻意没有实现什么

- RAG：与 Spring AI Stateful Agent 对标不需要，本章不加（留给第 4 章的结论对照）。
- Agentic 实验性 API：主路径不用，只在 `experiments/AgenticApiProbe` 留观察记录。
- 记忆提取（手写版 `MemoryExtractor` 从输入里发现偏好入库）：记忆靠测试和接口预置，
  只做检索和注入，与 Spring AI 版口径一致。
- 检查点/回放（checkpoint）、人工确认（HITL）、MCP、多智能体：留给后续章节。
- 向量检索：长期记忆与 Spring AI 版一致，只做关键字 LIKE，不做向量。
