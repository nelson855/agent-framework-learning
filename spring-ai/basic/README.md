# spring-ai/basic — 观察框架接管 Tool Loop

对应 Prompt：`docs/prompts/02_spring_ai_basic.md`
对应章节：`docs/chapters/02_SpringAI核心抽象.md`

本模块只做一件事：让模型自主连调 `createTask` → `getTask` 两个工具，
证明业务代码里没有任何手写 `while` 循环，多轮调用完全由框架驱动。

## 运行

```bash
cd spring-ai/basic
mvn -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository test
```

真实模型演示需要 OpenAI 兼容的模型服务。配置只从 `.env` 文件读取，不读环境变量：

```bash
# 仓库根目录复制模板后填入真实 Key（.env 已被忽略，不会提交）
cp ../../.env.example ../../.env
# 或只给本模块配：spring-ai/basic/.env（离运行位置近的先生效）

mvn -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository compile exec:java \
  -Dexec.mainClass=com.example.springai.basic.Main
```

无模型配置时演示直接退出并提示，不伪造结果；`mvn test` 的 16 个测试全部离线可跑。

## 实际调用序列（2026-09-14 真实演示，原样摘录，提示词已换成中文）

```text
model turn 1: user input -> 创建一个“学习 Spring AI Advisor”的任务，然后查询这个任务并告诉我任务编号和状态。
model turn 2: -> createTask(title=学习 Spring AI Advisor) -> tool result: 已创建任务 T-1，标题=学习 Spring AI Advisor，状态=OPEN
model turn 3: -> getTask(taskId=T-1) -> tool result: 任务 T-1，标题=学习 Spring AI Advisor，状态=OPEN
model turn 4: -> final answer: 任务已创建并查询完成：任务编号 T-1，状态 OPEN……
```

关键事实：`TaskAgent.chat()` 里只有一次 `prompt(...).call()`，
第 2、3 轮模型调用是框架的 ToolCallingAdvisor 自动发起的。

## 测试

| 测试 | 内容 | 是否依赖真实模型 |
|---|---|---|
| TaskServiceTest（4） | 创建、查询、空标题拒绝、未知编号返回空 | 否，内存 SQLite |
| TaskToolsTest（3） | 工具直调、未知编号返回找不到、每次调用留痕 | 否 |
| ChatClientWiringTest（2） | 两个工具解析出 `createTask` / `getTask` 回调；桩模型直返最终回答 | 否 |
| ModelConfigTest（7） | 超时格式解析、.env 解析、就近查找、缺文件缺键报错 | 否 |

## Version Notes

- Framework version: Spring AI 2.0.0（本地仓库现有版本；官方最新补丁为 2.0.1，差异仅为工具调用上限可配、严格模式默认关闭等补丁级改动，不影响本章 API）
- OpenAI Java SDK: openai-java-core 4.39.1 + openai-java-client-okhttp 4.39.1（与 spring-ai-openai 2.0.0 要求的 core 对齐）
- Jackson: 2.21.4（2.18.2 缺少 spring-ai 2.0.0 引用的 `JsonSerializeAs` 注解，运行时报错后升级）
- jsonschema-generator / module-jackson: 5.0.0（openai-java 会把 module-jackson 拉回 4.38.0，与 spring-ai 冲突，直接钉住）
- Spring Boot: 本章未用（见下）
- Docs checked at: 2026-09-11（Spring AI 官方 reference + GitHub releases）
- 教材 API 与当前 API 的差异：
  1. 2.0 把工具循环从各 ChatModel 实现里删掉，收归 `ToolCallingAdvisor`（`DefaultChatClient` 自动装配，整条链上只允许存在一个）。教材说的“框架管理递归 loop”指的就是它。
  2. 工具定义用 `@Tool(name, description)` + `@ToolParam`，传参用 `.tools(taskTools)`（单次）或 `.defaultTools(...)`（默认），与教材一致。
  3. Prompt 允许并推荐 Spring Boot，但 Boot 的自动装配需要 `spring-ai-starter-*`，本地仓库没有，只有底层 `spring-ai-client-chat` / `spring-ai-openai` 等包。为不引入不可用的 starter，本模块用纯 Java 手工装配（`OpenAiChatModel.builder()` + `ChatClient.create(...)`），等后续章节需要 Boot 集成时再补。结论不受影响：要观察的只是 Tool Loop 的归属。
  4. 2.0 的 `OpenAiChatModel.Builder` 要求同步和异步两个客户端都就绪（异步缺省会走 `OpenAiSetup` 读标准环境变量），本模块两个都手工配了，见 `Main`。
  5. 模型配置走项目级 `.env`（`CONFIG_AGENT_MODEL_*` 四个键，见仓库根 `.env.example`），代码不读环境变量；超时直接设在两个 OpenAI 客户端上。

## Framework Mapping

| 本项目中的能力 | 第一阶段手写组件 | 当前框架抽象 | 谁负责运行 | 仍需自己负责什么 |
|---|---|---|---|---|
| 模型调用 | LlmClient / OpenAiCompatibleLlmClient | ChatModel（OpenAiChatModel） | 框架调 HTTP | 选模型、配地址密钥、超时、网关特殊头 |
| 应用门面 | 无（直接调 LlmClient） | ChatClient（fluent 门面） | 框架组装请求 | 提示词、工具挂载 |
| Tool 定义 | Tool / ToolDefinition | `@Tool` 方法 + 自动生成的 JSON schema | 框架生成 schema | 工具名、描述、参数说明写清楚 |
| Tool 分派/执行 | ToolRegistry.execute | ToolCallingAdvisor + ToolCallingManager | 框架查表、调方法、回填结果 | 参数校验、错误语义（返回错误串还是抛异常） |
| Agent Loop | StatefulAgentRunner 双层 while | ToolCallingAdvisor 递归重入下游链 | 框架循环直到无工具调用 | 最大步数心智模型、出问题时定位 |
| 对话上下文 | AgentContext | Advisor 链内置会话历史 | 框架拼接 | 每轮放了什么、历史何时截断 |
| 业务数据 | TaskStore / task 表 | 无（还是自己的 TaskRepository） | 自己 | 全权负责 |
| 装配 | AppComponents | 手工 new（本章无 Boot） | 自己 | 同左 |

- **手写 AgentRunner 的 while 去哪了？** 进了 `ToolCallingAdvisor`：它是递归型 Advisor，收到含工具调用的模型回复就执行工具、把结果追加进会话历史、重新进入下游链，直到某轮回复不含工具调用为止。`TaskAgent.chat()` 全文无循环可以作证。
- **ToolRegistry / ToolExecutor 哪些职责消失？** 查表、分派、拼工具说明进系统提示、把工具结果送回模型，这四件都归框架了。`ToolCallbacks.from(taskTools)` 能直接解析出两个命名回调就是证据。
- **Tool Design 哪些职责仍然存在？** 名字和描述决定模型选不选得准；参数说明决定传参对不对；返回字符串的格式决定模型能不能接着用；出错时返回错误描述还是抛异常，决定模型能不能重试。本模块 `getTask` 查不到时返回 `task not found: xxx` 而不是抛异常，就是留给模型重试的余地。
- **框架如何继续下一轮模型调用？** Advisor 把工具结果以工具消息身份追加到会话历史，然后带着完整历史重新调模型。业务代码感知不到第二轮的存在，只能从控制台的 `[TOOL]` 日志里看到。
- **如何避免无限 Tool Loop？** 停止条件是“模型某轮不再发起工具调用”。本章工具语义简单（创建 → 查询 → 汇报），天然收敛；2.0.1 官方新增了可配的工具调用次数上限，当前用的 2.0.0 还没有该配置，真做长任务时要盯紧这条（后续章节补）。

## 本章刻意没有实现什么

Long-term Memory、RAG、MCP、Planning、Multi-Agent、Checkpoint，以及自定义复杂 Advisor Chain。原因：先把“框架接管 Tool Loop”这一个事实看清，不掺别的变量。
