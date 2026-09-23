# Ch04 Spring AI Advanced 设计文档

> 对应章节：`docs/chapters/04_SpringAI进阶_RAG_MCP_Observability.md`
> 对应 Prompt：`docs/prompts/04_spring_ai_advanced.md`
> 目标产物目录：`spring-ai/advanced/`
> 对比文档：`docs/comparisons/ch04_spring_ai_advanced.md`
> 日期：2026-09-22

## 0. 背景与现状

- TASKS.md 显示 Ch04 是下一章，前 3 章已确认完成。
- `spring-ai/advanced/src/` 当前是空目录，只有 `target/` 残留编译产物（未入 git），所以本章从零开始。
- 教材要求：RAG + MCP + Observability 三件事，"精简实验，不必复制完整 Stateful Agent"。

## 1. 已确认的关键选型

| 决策点 | 选择 | 理由 |
|---|---|---|
| Spring Boot | 不引入，纯 Java 手工装配 | 与 ch02 一致，聚焦观察 RAG/MCP/Observability 本身；ch03 已演示过 Boot 集成 |
| MCP 传输 | Stdio 进程间通信（主），In-process（测试/对照） | 教材重点是"跨进程/网络/信任边界"，stdio 最能体现 |
| Vector 存储 | `SimpleVectorStore`（内存） | Spring AI 官方内置，零外部依赖，离线可测 |
| Observability | 自定义 `RunTrace` 事件收集器 | 比 Micrometer/OTel 轻量，离线可测，与 ch03 风格一致 |

## 2. 模块结构

```
spring-ai/advanced/
├── pom.xml
├── README.md                              # Framework Mapping + 4 道必答题 + Version Notes
├── src/main/java/com/example/springai/advanced/
│   ├── Main.java                          # 演示入口（真实模型才走）
│   ├── ModelConfig.java                   # 沿用 ch02/ch03：读 .env
│   ├── AdvancedAgent.java                 # 主 Agent，一次 prompt 调用
│   ├── PolicyKnowledgeBase.java           # RAG：ingest + retrieval
│   ├── RetrievalAdvisor.java              # 自定义 Advisor，观察检索位置
│   ├── TaskRepository.java                # 内存任务存储（MCP Server 后端）
│   ├── TaskMcpServer.java                 # MCP Server 核心：tools/list + tools/call
│   ├── TaskMcpClient.java                 # MCP Client：发现 + 调用
│   ├── McpTaskToolCallback.java           # 把 MCP Tool 适配成 Spring AI ToolCallback
│   ├── McpServerMain.java                 # MCP Server 独立进程入口
│   ├── JsonRpcTransport.java              # 传输接口
│   ├── StdioProcessTransport.java         # stdio JSON-RPC 实现
│   ├── InProcessTransport.java            # 同 JVM 实现（测试用）
│   ├── RunTrace.java                      # 可观测性事件收集器
│   ├── TaskRecord.java / TaskStatus.java  # 领域 POJO
│   └── KeywordEmbeddingModel.java         # 离线可测的桩 EmbeddingModel
└── src/main/resources/kb/
│   └── task-policy.md                     # 知识库：3 段规范
└── src/test/java/com/example/springai/advanced/
    ├── KnowledgeBaseTest.java             # 3 个测试
    ├── McpBoundaryTest.java               # 4 个测试
    ├── RetrievalAdvisorTest.java          # 2 个测试
    └── RunTraceTest.java                  # 3 个测试
```

总计 15 个主代码类 + 4 个测试类，约 12 个测试方法。

## 3. Part A — RAG 设计

### 3.1 知识库

`src/main/resources/kb/task-policy.md`，3 段：

- `## BLOCKED 任务升级处理`：升级时限、通知对象、上报模板
- `## DONE 任务归档`：归档时机、归档位置
- `## 高优先级任务响应时限`：响应 SLA

### 3.2 ingest

`PolicyKnowledgeBase` 在构造时：
1. 读 classpath 下的 `kb/task-policy.md`
2. 按 `## ` 拆段
3. 对每段调 `EmbeddingModel.embed(String)` 得到向量
4. 存入 `SimpleVectorStore`，metadata 携带 `section_title`、`section_index`、`raw_text`

### 3.3 retrieval

`PolicyKnowledgeBase.search(String query, int topK)`：
1. 对 query 调 `embed()` 得查询向量
2. 在 `SimpleVectorStore` 内做相似度检索
3. 返回 List<Segment>，Segment 含 title / rawText / score

### 3.4 注入 Context —— 自定义 RetrievalAdvisor

**不用** Spring AI 的 `QuestionAnswerAdvisor`，因为要观察"检索在 Advisor chain 的哪个位置"。

```java
public class RetrievalAdvisor implements CallAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
        String query = extractUserText(req);
        List<Segment> hits = kb.search(query, 2);
        String augmented = "[KB]\n" + hits.join() + "\n[/KB]\n\n" + query;
        ChatClientRequest modified = req.mutate().prompt(augmented).build();
        trace.record(Kind.RETRIEVAL, hits.size(), hits.titles());
        return chain.nextCall(modified);
    }
}
```

**`@FW-CMP` 标记点**：在 `adviseCall` 上打 `[CONTEXT]`，说明"手写版在 `ContextBuilder.build()` 里手动拼 prompt；框架版通过 Advisor 在 chain 中拦截并改写请求"。

### 3.5 离线 Embedding

`KeywordEmbeddingModel implements EmbeddingModel`：把文本 token 化、按关键词 hash 到固定维度向量。相同关键词 → 向量相近，保证离线可测且语义可预测。

## 4. Part B — MCP 设计

### 4.1 协议面

只实现 MCP 协议的两个方法：

- `tools/list`：返回 `get_task_status` 的 JSON Schema
- `tools/call`：执行查询，参数 `{ "task_id": "T-1" }`

传输层用 JSON-RPC 2.0（`jsonrpc`/`id`/`method`/`params` 四元组）。

### 4.2 MCP Server

`TaskMcpServer`：核心 handler，接 `JsonRpcTransport` 接口，读到 `tools/list` 返回 schema，读到 `tools/call` 调 `TaskRepository.findById()`。

`McpServerMain`：独立进程入口，main 方法里 new `TaskMcpServer` + `StdioProcessTransport(System.in, System.out)`。

`TaskRepository`：构造时塞 3 条假数据（T-1 OPEN、T-2 BLOCKED、T-3 DONE）。不接 DB，符合"本地教学 MCP Server"要求。

### 4.3 MCP Client

`TaskMcpClient`：
- 构造时启子进程（真实模式）或接受 in-process transport（测试模式）
- `discover()`：发 `tools/list`，缓存结果到 `List<DiscoveredTool>`
- `call(name, args)`：发 `tools/call`，返回结果字符串或抛 `McpException`

### 4.4 适配 Spring AI

`McpTaskToolCallback implements ToolCallback`：
- `getToolDefinition()`：把 MCP schema 转成 Spring AI `ToolDefinition`
- `call(String toolInput)`：解析 JSON 参数 → 调 `TaskMcpClient.call()` → 把返回值/异常转成字符串

错误语义分三种（都要让模型能继续）：
- 任务不存在 → 返回 `"task not found: T-99"`（不抛异常，让模型重试）
- 参数缺失/非法 → 返回 `"invalid argument: ..."`
- 传输/协议错误 → 抛 `McpException`，由框架兜底

### 4.5 传输接口

```java
public interface JsonRpcTransport extends AutoCloseable {
    JsonRpcResponse send(JsonRpcRequest req);
}
```

- `StdioProcessTransport`：fork `java -cp ... McpServerMain` 子进程，stdin/stdout 各一根管道
- `InProcessTransport`：同 JVM 内直接调 `TaskMcpServer.handle(req)`

### 4.6 `@FW-CMP` 标记点

- `TaskMcpServer` 类级 Javadoc：`[TOOL_DISPATCH]` 对应手写版 `ToolRegistry.execute()`
- `McpTaskToolCallback.call()` 行内：`[TOOL_DISPATCH]` 说明远程调用替代了本地反射调用
- `StdioProcessTransport` 类级 Javadoc：`[HTTP]` 说明手写版只有 HTTP client，现在多了进程管道

## 5. Part C — Observability 设计

### 5.1 RunTrace

```java
public class RunTrace {
    public enum Kind { MODEL_CALL, RETRIEVAL, MCP_TOOL_CALL, FINAL_RESPONSE }
    public record Event(Kind kind, Instant at, String summary, Map<String,Object> attrs) {}

    public void record(Kind kind, String summary, Map<String,Object> attrs);
    public List<Event> events();
    public String format();   // 树状打印
}
```

### 5.2 埋点位置

| 位置 | 事件 | 说明 |
|---|---|---|
| `AdvancedAgent.chat()` 调用前 | `MODEL_CALL` | 记录 prompt 摘要 |
| `RetrievalAdvisor.adviseCall()` | `RETRIEVAL` | 记录命中数、段标题 |
| `McpTaskToolCallback.call()` | `MCP_TOOL_CALL` | 记录工具名、参数、结果长度、成功/失败 |
| `AdvancedAgent.chat()` 返回前 | `FINAL_RESPONSE` | 记录最终回答长度 |

第二次模型调用（工具结果回填后）由 ToolCallingAdvisor 自动发起，本章在 `AdvancedAgent` 外层不再单独记录，但 `MCP_TOOL_CALL` 事件的出现已经能证明流程进入了第二轮。

### 5.3 输出格式（演示）

```
run a3f8c2
├─ 10:15:23.411 MODEL_CALL         prompt="根据规范，告诉我..."
├─ 10:15:23.587 RETRIEVAL          hits=2 sections=[BLOCKED 任务升级处理, 高优先级任务响应时限]
├─ 10:15:24.012 MCP_TOOL_CALL      tool=get_task_status task_id=T-2 result=BLOCKED ok=true
├─ 10:15:24.445 MODEL_CALL         prompt=<回填后>
└─ 10:15:24.890 FINAL_RESPONSE     len=156
```

### 5.4 `@FW-CMP` 标记点

- `RunTrace` 类级 Javadoc：`[TRACE]` 对应手写版 `agent-harness/Trace` 的手写 println
- 说明：手写版只能"打字符串"，框架版通过 Advisor / ToolCallback / ChatClient 三层埋点，产出结构化事件

## 6. 测试设计（全部离线）

| 测试类 | 用例数 | 覆盖 |
|---|---|---|
| `KnowledgeBaseTest` | 3 | ingest 切段数、关键词命中 BLOCKED 段、空查询返空 |
| `McpBoundaryTest` | 4 | tools/list schema、命中查询、未知任务、缺参数错误 |
| `RetrievalAdvisorTest` | 2 | 检索结果注入到 user message、命中数被 RunTrace 记录 |
| `RunTraceTest` | 3 | 四种事件记录、时序单调、属性字段完整 |

总计 12 个离线测试。

## 7. 文档产物

### 7.1 `spring-ai/advanced/README.md`

按 §8 规范包含 Framework Mapping 表（8 行能力维度）+ 4 道必答题：

1. RAG 对应第一阶段哪个 ContextBuilder 流程？
2. MCP 改变了 Tool 的哪一层边界？
3. Observability 与手写 Trace 的差别是什么？
4. 框架是否自动解决了远程 Tool 的权限问题？为什么没有？

加 `Version Notes`：Spring AI 2.0.0，Docs checked at: 2026-09-22。

### 7.2 `docs/comparisons/ch04_spring_ai_advanced.md`

按 §6.1 模板：

- 差异点导航表
- 按"运行时顺序"展开：启动装配 → ingest → 用户提问 → Advisor 拦截 → 检索注入 → 模型调用 → MCP 工具调用 → 模型第二轮 → final answer
- 每节贴手写完整方法 + 框架完整调用 + 差异本质表 + 取舍
- 新增类别定义：`[RETRIEVAL]`（本章新增，规范 §5.3 表里没有，需要在对比文档中定义并补回规范表）
- 末尾列"本章刻意没做什么"：Long-term Memory、Planning、Multi-Agent、Checkpoint、HITL、真实 Embedding 模型、真实 MCP Server 账户

### 7.3 Java 代码 `@FW-CMP` 标记

预期打标记位置：

- `AdvancedAgent`（类级 Javadoc）
- `RetrievalAdvisor`（类级 + `adviseCall` 行内）
- `TaskMcpServer`（类级）
- `TaskMcpClient`（类级）
- `McpTaskToolCallback.call()`（行内）
- `StdioProcessTransport`（类级）
- `RunTrace`（类级）

## 8. 执行顺序（高层）

1. 清理 `spring-ai/advanced/target/` 旧产物
2. 写 `pom.xml`（继承 ch03 配置 + 新增 `spring-ai-core` 的 VectorStore/EmbeddingModel 依赖）
3. 写领域 POJO + `TaskRepository` + `ModelConfig`
4. 写 `KeywordEmbeddingModel` + `PolicyKnowledgeBase`
5. 写 `RunTrace` + `RetrievalAdvisor`
6. 写 MCP 层（transport → server → client → tool callback）
7. 写 `AdvancedAgent` + `Main`
8. 写 4 个测试类
9. 跑 `mvn test` 全绿
10. 写 README + 对比文档 + `@FW-CMP` 标记
11. 更新 `TASKS.md` 勾选 Ch04

## 9. 刻意不做的事

- 不接真实 Embedding 模型（离线优先）
- 不接真实第三方 MCP Server（本地教学版）
- 不引入 Micrometer / OpenTelemetry（自定义 RunTrace 就够）
- 不引入 Spring Boot（本章用纯 Java 手工装配）
- 不实现 RAG 的 re-rank / hybrid search
- 不实现 MCP 的 prompts/resources/roots 等其他能力
- 不实现 Observability 的分布式追踪（单 JVM 就够）
