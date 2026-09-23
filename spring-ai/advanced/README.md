# spring-ai/advanced — RAG + MCP + Observability 进阶实验

对应 Prompt：`docs/prompts/04_spring_ai_advanced.md`
对应章节：`docs/chapters/04_SpringAI进阶_RAG_MCP_Observability.md`

本模块只做一件事：一次提问里同时走**知识库检索（RAG）**与**远程工具调用（MCP）**，
并用一条结构化轨迹（`RunTrace`）把"检索什么时候发生、MCP 工具什么时候发生"看清楚。

任务场景（来自 Prompt 04）：

```text
"根据规范，告诉我 BLOCKED 任务应该怎么处理；如果我提供了任务号，再查询任务当前状态。"
```

## 运行

```bash
mvn -f spring-ai/advanced/pom.xml -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository test
```

真实模型演示需要 OpenAI 兼容的模型服务。配置只从 `.env` 文件读取，不读环境变量：

```bash
# 仓库根目录复制模板后填入真实 Key（.env 已被忽略，不会提交）
cp ../../.env.example ../../.env

mvn -f spring-ai/advanced/pom.xml -Dmaven.repo.local=/Users/nelson/software/apache-maven-3.8.4/repository compile exec:java \
  -Dexec.mainClass=com.example.springai.advanced.Main
```

无模型配置时演示直接退出并提示，不伪造结果；`mvn test` 的 12 个测试全部离线可跑。
演示会 fork 一个 `McpServerMain` 子进程，结束时自动回收，无需手工启停。

## 实验观察点

一次真实 run 的轨迹长这样（`RunTrace.format()` 输出）：

```text
run a3f8c2
├─ MODEL_CALL         prompt="根据规范，BLOCKED 任务应该怎么处理？…"
├─ RETRIEVAL          hits=2 sections=[BLOCKED 任务升级处理, 高优先级任务响应时限]
├─ MCP_TOOL_CALL      tool=get_task_status task_id=T-2 result=任务 T-2…状态=BLOCKED ok=true
├─ MODEL_CALL         prompt=<工具结果回填后>
└─ FINAL_RESPONSE     len=156
```

四个观察结论：

1. **检索发生在第一次模型调用之前**，在 `RetrievalAdvisor.adviseCall` 里，
   命中片段以 `[知识库片段]` 前缀拼进用户消息，模型看到的是增强后的提问。
2. **MCP 工具调用发生在模型第二轮**：框架的 ToolCallAdvisor 收到含工具调用的回复，
   经 `McpTaskToolCallback` 把 JSON-RPC 发到独立进程，结果回填后再调模型。
3. **trace 串起两者**：`RunTrace` 的事件顺序就是 run 的真实层次，
   不需要猜"检索和工具调用谁先谁后"。
4. **Context 是否重复**：检索片段进一次（Advisor 注入），工具结果进一次（框架回填），
   两者内容不同（规范 vs 任务现状），无重复；若 topK 设太大或工具返回整段规范，
   才可能出现重复——这是调参问题，不是机制问题。

## 测试

| 测试 | 内容 | 是否依赖真实模型 |
|---|---|---|
| KnowledgeBaseTest（3） | 切段数为 3、BLOCKED 提问命中 BLOCKED 段、空查询返空 | 否，KeywordEmbeddingModel 桩 |
| McpBoundaryTest（4） | tools/list 的 schema、T-2 命中、未知任务号、缺参数报错 | 否，InProcessTransport |
| RetrievalAdvisorTest（2） | 检索片段注入用户消息、命中数被 RunTrace 记录 | 否，桩 Advisor 链 |
| RunTraceTest（3） | 四种事件顺序、属性保留、树状打印 | 否 |

## Version Notes

- Framework version: Spring AI 2.0.0（与 ch02/ch03 一致，本地仓库现有版本）
- 新增依赖：`spring-ai-vector-store`（SimpleVectorStore）、`spring-ai-model`（EmbeddingModel 接口），均为 BOM 2.0.0 管理，无版本冲突
- Jackson / openai-java / jsonschema 钉版与 ch02 完全一致（见 ch02 README）
- Spring Boot：本章未用（手工装配，见下）
- Docs checked at: 2026-09-22（Spring AI 官方 reference + 本地 sources jar 反查 API 签名）
- 教材 API 与当前 API 的差异：
  1. 2.0 的工具循环 Advisor 叫 `ToolCallAdvisor`（ch02 README 写的是旧名 `ToolCallingAdvisor`，指同一东西），`DefaultChatClient` 自动装配，链上只允许存在一个。
  2. `CallAdvisor` 接口在 `org.springframework.ai.chat.client.advisor.api` 包，方法为 `adviseCall(ChatClientRequest, CallAdvisorChain)`；`ChatClientRequest` 是 record，改写请求用 `mutate().prompt(...).build()`。
  3. `VectorStore` 接口只声明 `add`/`delete`，`similaritySearch` 在父接口 `VectorStoreRetriever` 里；`SimpleVectorStore.builder(embeddingModel).build()` 即开即用，无需外部向量库。
  4. `ToolCallback` 接口方法为 `getToolDefinition()` + `call(String toolInput)`；`ToolDefinition.builder()` 返回 `DefaultToolDefinition.Builder`，`inputSchema` 直接传 JSON 字符串。
  5. 本章没用 `spring-ai-starter-mcp-client`：它要求 Spring Boot 自动装配，且封装后看不见"发现→调用"两步。本章纯 Java 手写 60 行 stdio 传输，教学上更透明。
  6. 本章没用 `QuestionAnswerAdvisor`：它的检索包在内部，与"观察检索在 Advisor chain 的哪个位置"的目标冲突，改用自写 `RetrievalAdvisor`。

## Framework Mapping

| 本项目中的能力 | 第一阶段手写组件 | 当前框架抽象 | 谁负责运行 | 仍需自己负责什么 |
|---|---|---|---|---|
| 模型调用 | LlmClient / OpenAiCompatibleLlmClient | ChatModel（OpenAiChatModel） | 框架调 HTTP | 选模型、配地址密钥、超时（同 ch02） |
| 应用门面 | 无 | ChatClient（fluent 门面） | 框架组装请求 | 提示词、工具挂载、Advisor 挂载 |
| 知识入库 | KnowledgeStore（内存 Map + 关键词匹配） | SimpleVectorStore + EmbeddingModel | 框架做向量化、存、相似度检索 | 切段策略、metadata 设计、Embedding 选型 |
| 检索注入 | ContextBuilder.build（手动拼字符串） | RetrievalAdvisor（CallAdvisor 链） | 框架在调模型前回调 | 检索时机、topK、注入格式、getOrder 顺序 |
| Tool 定义 | Tool 接口（同进程实现类） | MCP tools/list 返回的 JSON Schema | Server 端定义，Client 端发现 | Schema 写清楚（决定模型传参对不对） |
| Tool 分派/执行 | ToolRegistry.execute（同进程 Map 查表） | McpTaskToolCallback → JSON-RPC → TaskMcpServer | Server 端查表执行，管道传结果 | 参数校验、错误语义、进程生命周期 |
| Agent Loop | StatefulAgentRunner 双层 while | ToolCallAdvisor 递归重入下游链 | 框架循环直到无工具调用 | 同 ch02，另加"框架不知道 MCP 工具是远程的" |
| 轨迹记录 | TraceService（散落日志字符串） | RunTrace（自研结构化事件） | 自己收集 | 四个埋点位置、事件属性设计 |
| MCP 传输 | 无（手写版没有远程工具） | 自研 JsonRpcTransport（stdio/in-process） | 自己（启停子进程、管道读写） | 进程崩溃、管道断开、超时三类新故障 |
| 业务数据 | TaskStore / task 表 | 内存 TaskRepository（MCP Server 后端） | 自己 | 全权负责 |
| 装配 | AppComponents | 手工 new（本章无 Boot） | 自己 | 同 ch02，另加 MCP 子进程启停 |

- **RAG 对应第一阶段哪个 ContextBuilder 流程？** 对应 `ContextBuilder.build` 里"检索记忆→拼进上下文"这段。本章的 `RetrievalAdvisor.adviseCall` 就是它的框架版：时机从"业务代码写死的一步"变成"Advisor 链上的一环"，检索内容以 `[知识库片段]` 前缀注入，而不是 `[memory retrieved]`。
- **MCP 改变了 Tool 的哪一层边界？** 改变了分派层。定义层（名字/描述/Schema）还在，但从"编译时写死"变成"运行时发现"；执行层从"同进程方法调用"变成"跨进程 JSON-RPC 请求"。查表、分派、结果回填的逻辑还在，只是物理位置从 Client 进程搬到了 Server 进程。
- **Observability 与手写 Trace 的差别是什么？** 手写 `TraceService` 只能回答"发生过什么"（散落字符串）；`RunTrace` 能回答"层次是什么"（MODEL_CALL → RETRIEVAL → MCP_TOOL_CALL → FINAL_RESPONSE 的顺序即 run 的结构）。差别不在"记没记"，而在事件带类型和顺序，能还原一次 run 的因果链。
- **框架是否自动解决了远程 Tool 的权限问题？为什么没有？** 没有。本章 `get_task_status` 是只读工具所以没设防，但如果是写操作，框架不会替你做鉴权、审批、审计——`ToolExecutor` 在手写版里有的高风险门禁（`highRiskApproved`），MCP 版里要自己在 Server 端重建。框架只解决"怎么调通"，不管"该不该调"。
- **删除了哪些手写代码？** `KnowledgeStore` 的关键词匹配（被向量检索替代）、`ToolRegistry` 的 Map 查表（被 tools/list 发现替代，同进程部分）、`ContextBuilder` 的手动拼串（被 Advisor 改写替代）。
- **哪些复杂度只是转移了？** 相似度算法（进了 SimpleVectorStore 黑盒）、工具分派（进了 MCP Server 进程）、循环控制（进了 ToolCallAdvisor）。切段、Schema、错误语义一个没少，全在自己手里。
- **控制权减少在哪里？** 看不见第二轮模型调用的 prompt（框架回填的）；控制不了 SimpleVectorStore 的相似度算法；MCP 工具的执行时机完全由模型经框架决定。
- **调试方式的变化？** ch02 靠 `[TOOL]` 日志反推循环；本章靠 `RunTrace` 事件顺序直接看到层次。MCP 出问题先看 Server 端（`handleLine` 逻辑）还是 Client 端（管道），要分两段查。
- **如果不用该框架，需要重新实现什么？** 向量检索（或退回关键词匹配）、Advisor 链（或退回手动拼提示）、MCP 协议（或退回同进程工具）、Tool Loop（ch02 已论述）。

## 本章刻意没有实现什么

Long-term Memory（用户偏好类记忆，非知识检索）、Planning、Multi-Agent、Checkpoint、HITL 人工审批、真实 Embedding 模型、真实第三方 MCP Server、Micrometer/OpenTelemetry 接入、RAG 重排（re-rank）与混合检索、MCP 的 prompts/resources/roots 能力、Spring Boot 集成。原因：本章目标是"看清 RAG 何时发生、MCP 何时发生、trace 如何串起两者"三件事，不掺别的变量。
