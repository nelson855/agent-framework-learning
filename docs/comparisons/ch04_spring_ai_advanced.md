# Ch04 Spring AI Advanced — 手写 vs 框架 完整对比

> 本文件是第 4 章（RAG + MCP + Observability）的完整对比文档，对应代码模块 `spring-ai/advanced`。
> 代码里所有差异点都打了 `@FW-CMP` 标记；每个标记末尾有 `#N-xxx` 锚点指向本文档对应章节。

## 阅读指引：本章相对第三章新增了什么

> 第三章讲的是"带状态的助手"：历史对话、用户偏好、计划推进怎么做。
> 本章讲的是"进阶三件套"：检索增强生成（Retrieval-Augmented Generation，RAG，指提问时先查规范文档再回答）、
> 模型上下文协议（Model Context Protocol，MCP，指工具搬到独立进程、用统一协议调用）、可观察性（指一次运行内的阶段轨迹）。
> 两份文档共用同一套分类标签，所以标题看着撞衫，下表是每节和第三章的关系，正文每节开头又各重复一句，方便跳读：

| # | 本章 | 和第三章的关系 |
|---|---|---|
| 1 | 工具循环收编检索 | 第三章只收编了"调模型→调工具"；本章多收编了"调模型前的检索" |
| 2 | 规范文档向量检索 | 第三章的记忆是"记用户偏好"；本章是"查规范文档"，只有名字像 |
| 3 | 检索结果注入 | 第三章注入的是历史和偏好；本章注入的是查到的规范片段，手法同、来源不同 |
| 4 | 工具跨进程调用 | 第三章工具还在同一进程内；本章工具搬到独立进程，是全新内容 |
| 5 | 工具传输管道 | 手写版和第三章都没有这个概念；本章全新 |
| 6 | 单次运行轨迹 | 第三章看的是"执行到第几步"的业务进度；本章看的是"检索和工具何时发生"的单次轨迹 |

> MCP 概念本身（三种能力、和 HTTP 的区别）：见 `docs/07_Agent背景知识.md` 第 1–2 节，本章只写框架版和手写版的差别。

## 本章节差异点导航

| # | 类别 | 位置（框架版） | 一句话差异 |
|---|---|---|---|
| 1 | TOOL_LOOP | `AdvancedAgent.chat()` | 检索+工具循环全收进一次 `prompt().call()`，业务代码无循环 |
| 2 | MEMORY | `PolicyKnowledgeBase` 构造器 | 关键词匹配 → 向量检索，切段仍自己做 |
| 3 | CONTEXT | `RetrievalAdvisor.adviseCall()` | 检索注入从"手动拼串"变成"Advisor 链上改写请求" |
| 4 | TOOL_DISPATCH | `McpTaskToolCallback.call()` | 工具分派从同进程 Map 查表变成跨进程 JSON-RPC |
| 5 | HTTP | `StdioProcessTransport` | 新增"调工具要走管道"的传输层，手写版没有这个概念 |
| 6 | TRACE | `RunTrace` | 散落日志字符串 → 带类型与顺序的结构化事件 |

---

## 1. TOOL_LOOP — 一次调用收编检索与工具循环 {#1-tool-loop}

**类别**：`TOOL_LOOP`
**框架版位置**：`spring-ai/advanced/src/main/java/com/example/springai/advanced/AdvancedAgent.java` → `chat()`

> 与第三章的关系：第三章第 3 节讲过单步"调模型→调工具"循环是怎么收进一次调用的，那部分结论本节不再重复；
> 本节只讲多出来的半步——"调模型之前先检索规范"，以及检索和工具怎么被收进同一次调用。

### 手写版（agent-harness）

手写版 `AgentRunner.loop`（ch02 对比文档已贴过全貌，核心骨架）：

```java
for (int step = 0; step < maxSteps; step++) {
    String reply = svc.model.chat(runId, null, context);   // 调模型
    AgentDecision d = AgentDecisionParser.parse(reply);    // 解析
    if (d.isFinal()) return RunResult.completed(d.answer());
    ToolResult r = svc.toolExec.execute(d.toolCall(), ...); // 执行工具
    context.add(Message.assistant(reply));                 // 手动回填
    context.add(Message.user(observation(call, r)));
}
```

如果手写版要加 RAG，循环里还要多一段：每次调模型前先 `knowledge.search()` 再拼进 context。

### 框架版（本模块）

```java
String finalAnswer = ChatClient.create(chatModel)
        .prompt()
        .advisors(retrieval)              // 检索增强插在调模型前
        .system(SYSTEM_PROMPT)
        .user(userInput)
        .tools(toolCallbacks.toArray())   // MCP 工具当普通 ToolCallback 挂载
        .call()                           // ToolCallAdvisor 内部递归，直到无工具调用
        .content();
```

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 循环载体 | `for` 循环，业务代码直写 | `ToolCallAdvisor` 递归重入下游链 |
| 检索位置 | 循环体内手动调 `knowledge.search()` | `RetrievalAdvisor` 在链上自动回调 |
| 工具身份 | 同进程 `Tool` 接口实现 | `ToolCallback`（背后是独立进程，框架不知道） |
| 轮数可见性 | `step` 变量直接可见 | 不可见，只能从 `RunTrace` 事件数反推 |

### 取舍

- 得：`AdvancedAgent.chat()` 全文无循环，加 RAG/MCP 不增加编排代码。
- 失：看不到第二轮 prompt 长什么样（框架回填的）；MCP 工具的远程身份对框架透明，出问题时框架日志帮不上忙。
- 框架不管：MCP 子进程的启停（`Main` 里手动 `spawn/close`）。

### 实际运行证据

`RetrievalAdvisorTest` 证明检索确实发生在链上（请求被改写后才到下游）；
`McpBoundaryTest` 证明工具经 JSON-RPC 可达。真实多轮需接模型，见 README 观察点。

---

## 2. MEMORY — 知识入库：关键词匹配 → 向量检索 {#2-memory}

**类别**：`MEMORY`
**框架版位置**：`spring-ai/advanced/src/main/java/com/example/springai/advanced/PolicyKnowledgeBase.java` → 构造器

> 与第三章的关系：第三章第 7 节的记忆是"记住用户偏好"（比如用户爱用什么标题格式），存数据库、按关键词查；
> 本节的记忆是"查规范文档"（比如升级流程写在哪段），走向量查语义相似。两者只有名字像，存的东西、查的办法都不同。

### 手写版（agent-harness）

`references/handwritten-agent-v1/agent-harness/src/main/java/com/example/agentlearning/stage04/KnowledgeStore.java`（完整）：

```java
public final class KnowledgeStore {

    private final Map<String, Doc> docs = new LinkedHashMap<>();

    /** 构建默认的演示知识。 */
    public KnowledgeStore() {
        put("高风险操作规范",
                "高风险操作规范\n" +
                        "1. 删除任务（deleteTask）属于高风险操作。\n" +
                        "2. 执行 deleteTask 前必须经过人工审批（Approval）。\n" +
                        "3. 审批通过后系统才真正执行删除。\n" +
                        "4. 未获得人工批准的删除请求将被拒绝。");
        put("任务管理规范",
                "任务管理规范\n" +
                        "1. 每个任务有 id、title、status（OPEN/IN_PROGRESS/DONE）。\n" +
                        "2. 创建任务时默认 status=OPEN。\n" +
                        "3. 任务可通过 createTask 工具创建，通过 deleteTask 删除（需审批）。\n" +
                        "4. 对知识不熟悉时先用 getDoc 查询。");
    }

    public KnowledgeStore put(String title, String content) {
        docs.put(title, new Doc(title, content));
        return this;
    }

    /** 按完全匹配标题查询。 */
    public Doc findByTitle(String title) {
        return docs.get(title);
    }

    /** 按关键词检索多个相关文档。 */
    public List<Doc> findByKeyword(String keyword) {
        String kw = keyword.toLowerCase();
        List<Doc> hits = new ArrayList<>();
        for (Doc d : docs.values()) {
            if (d.title().toLowerCase().contains(kw) || d.content().toLowerCase().contains(kw)) {
                hits.add(d);
            }
        }
        return hits;
    }

    public List<String> allTitles() {
        return List.copyOf(docs.keySet());
    }

    public record Doc(String title, String content) {
    }
}
```

要点：存储是 `LinkedHashMap`，检索是 `String.contains` 关键词匹配，无向量概念。

### 框架版（本模块）

```java
this.store = SimpleVectorStore.builder(embeddingModel).build();
List<Document> documents = new ArrayList<>(parsed.size());
for (int i = 0; i < parsed.size(); i++) {
    Segment segment = parsed.get(i);
    // 标题是段落内容的一部分，一起入库，检索词命中标题也算命中该段。
    documents.add(new Document(segment.title() + "\n" + segment.text(),
            Map.of("section_title", segment.title(), "section_index", i)));
}
this.store.add(documents);
```

检索：

```java
List<Document> hits = store.similaritySearch(
        SearchRequest.builder().query(query).topK(topK).build());
```

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 切段 | 无（整篇 Doc 存 Map） | 自己按 `## ` 切（`parseSections`） |
| 向量化 | 无 | `EmbeddingModel.embed()`，框架调 |
| 存储 | `LinkedHashMap` | `SimpleVectorStore` |
| 检索算法 | `contains` 关键词匹配 | 余弦相似度，框架算 |
| 来源追踪 | 无（返回整篇 Doc） | metadata 带 `section_title`/`section_index` |

### 取舍

- 得：语义相近但字面不同也能命中；不用自己写相似度。
- 失："怎么算相似"不可见——开发中就遇到过通用词"任务"淹没关键词"BLOCKED"，靠把桩模型改成二值词袋才稳定（见 `KeywordEmbeddingModel` 注释）。
- 框架不管：切段策略（按标题/按长度/按 token）、metadata 设计、Embedding 选型。换向量库要跟着换 `VectorStore` 实现。

### 实际运行证据

`KnowledgeBaseTest.blockedQueryHitsBlockedSectionFirst`：提问"BLOCKED 任务应该怎么处理"，
第一命中是"BLOCKED 任务升级处理"段，且原文含"4 小时内发起升级"，来源可追踪。

---

## 3. CONTEXT — 检索注入：手动拼串 → Advisor 改写请求 {#3-context}

**类别**：`CONTEXT`
**框架版位置**：`spring-ai/advanced/src/main/java/com/example/springai/advanced/RetrievalAdvisor.java` → `adviseCall()`

> 与第三章的关系：往提示里塞东西的手法和第三章第 2 节一样（都是挂一个顾问、不改主流程）；
> 不同的是塞的东西：第三章塞的是历史对话和用户偏好，本节塞的是刚查到的规范片段。

### 手写版（agent-harness）

`references/handwritten-agent-v1/agent-harness/src/main/java/com/example/agentlearning/stage04/ContextBuilder.java`（完整）：

```java
public final class ContextBuilder {

    private final ToolProvider tools;
    private final MemoryStore memory;
    private final TraceService trace;

    public ContextBuilder(ToolProvider tools, MemoryStore memory, TraceService trace) {
        this.tools = tools;
        this.memory = memory;
        this.trace = trace;
    }

    /** 构建基础上下文（system + 用户目标 + 初始记忆命中）。workerId 为 null 表示主流程。 */
    public List<Message> build(String runId, String workerId, String goal,
                               List<PlanStep> plan, String userMessage) {
        List<Message> ctx = new ArrayList<>();
        ctx.add(Message.system(systemPrompt(plan)));
        ctx.add(Message.user(userMessage));

        List<String> hits = memory.retrieve(goal);
        if (!hits.isEmpty()) {
            for (String hit : hits) {
                ctx.add(Message.user("[memory retrieved] " + hit));
            }
            trace.record(TraceEvent.of(runId, workerId, TraceEventType.MEMORY_RETRIEVED,
                    "检索到记忆 " + hits.size() + " 条"));
        }
        trace.record(TraceEvent.of(runId, workerId, TraceEventType.CONTEXT_BUILT,
                "构建上下文 n=" + ctx.size() + "，计划 " + plan.size() + " 步"));
        return ctx;
    }

    /** 在循环中重新检索记忆并注入新增命中（remember 写入后模型即可看到）。返回注入后计数。 */
    public int injectMemory(List<Message> ctx, String runId, String workerId,
                            String goal, int alreadyInjected) {
        List<String> hits = memory.retrieve(goal);
        int injected = alreadyInjected;
        for (int i = injected; i < hits.size(); i++) {
            ctx.add(Message.user("[memory retrieved] " + hits.get(i)));
            injected++;
        }
        if (injected > alreadyInjected) {
            trace.record(TraceEvent.of(runId, workerId, TraceEventType.MEMORY_RETRIEVED,
                    "新增注入记忆 " + (injected - alreadyInjected) + " 条"));
        }
        return injected;
    }

    private String systemPrompt(List<PlanStep> plan) {
        // ……（系统提示词组装，略，详见基线源码）
        return """
                你是一个 Harness 编排 Agent。可以调用下面的工具：
                %s

                本轮计划（供参考）：
                %s

                决策协议（只输出一个 JSON，不要输出任何其他文字）：
                1. 需要调用工具时：
                   {"type":"tool_call","tool":"工具名","arguments":{...},"decisionSummary":"一步解释"}
                2. 已有足够信息可回答时：
                   {"type":"final","answer":"给用户的最终回答"}

                安全规则：deleteTask 是高风险操作，会提交人工审批；审批前不会真正删除。
                对话中带 [observation] / [memory retrieved] 前缀的消息是工具执行或记忆检索结果，
                请基于它们继续决策；不要重复执行已失败或已在等待审批的调用。
                """.formatted(tools.toolsInstruction(), planText.toString().trim());
    }
}
```

要点：检索（`memory.retrieve`）和拼接（`ctx.add`）都是业务代码在调模型前手动做的，
调用点写死在 `build()` 里。

### 框架版（本模块）

```java
@Override
public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    String userText = extractUserText(request.prompt());
    List<PolicyKnowledgeBase.Segment> hits = knowledgeBase.search(userText, topK);
    if (!hits.isEmpty()) {
        StringBuilder augmented = new StringBuilder("[知识库片段]\n");
        // ……拼接片段……
        request = request.mutate()
                .prompt(withRewrittenUserMessage(request.prompt(), augmented.toString()))
                .build();
    }
    trace.record(RunTrace.Kind.RETRIEVAL, ...);
    return chain.nextCall(request);
}
```

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 触发方式 | 业务代码显式调 `build()` | 框架在调模型前回调 `adviseCall` |
| 拼接位置 | 新起一条 `Message.user` 追加到列表尾 | 改写最后一条用户消息（前缀 `[知识库片段]`） |
| 顺序控制 | 调用顺序即代码顺序 | `getOrder()` 决定链上先后（本类返回 0，要早于调模型的 Advisor） |
| 复用性 | `build()` 写死在本流程 | Advisor 可插拔，挂到任何 ChatClient 上都生效 |

### 取舍

- 得：检索逻辑与编排解耦；换注入格式只改 Advisor。
- 失：链上有多个 Advisor 时要盯 `getOrder`，否则可能出现"先截断历史再检索"这类顺序 bug；改写后的完整 prompt 不在业务代码里，想看得抓 `CapturingChain`（测试即此法）。
- 框架不管：topK、注入格式、"检索落空时是否放行"（本章选择放行原文）。

### 实际运行证据

`RetrievalAdvisorTest.advisorInjectsRetrievedSegmentsIntoUserMessage`：
桩链捕获到改写后的请求，用户消息同时含原始提问、命中标题、命中原文。

---

## 4. TOOL_DISPATCH — 工具分派：同进程查表 → 跨进程 JSON-RPC {#4-tool-dispatch}

**类别**：`TOOL_DISPATCH`
**框架版位置**：`spring-ai/advanced/src/main/java/com/example/springai/advanced/McpTaskToolCallback.java` → `call()`

> 与第三章的关系：第三章第 5 节讲"工具说明书怎么写"（注解替代手写表格），工具本身还在同一进程内；
> 本节讲的是下一步——工具搬到独立进程后怎么调。说明书那部分结论沿用，本节只讲跨进程多出来的发现、传输和错误分层。

### 手写版（agent-harness）

`references/handwritten-agent-v1/agent-harness/src/main/java/com/example/agentlearning/stage04/ToolExecutor.java`（完整）：

```java
public final class ToolExecutor {

    /** deleteTask 未获批准时的失败标记。 */
    public static final String HIGH_RISK_DENIED = "高风险操作需人工批准";

    private final ToolRegistry registry;
    private final TraceService trace;

    public ToolExecutor(ToolRegistry registry, TraceService trace) {
        this.registry = registry;
        this.trace = trace;
    }

    /**
     * 执行一次工具调用。
     *
     * @param highRiskApproved 仅 deleteTask 需要：人工批准后才可为 true
     */
    public ToolResult execute(ToolCall call, String runId, String workerId, boolean highRiskApproved) {
        long t0 = System.currentTimeMillis();
        String args = abbreviate(call.arguments().toString());

        trace.record(TraceEvent.of(runId, workerId, TraceEventType.TOOL_CALL,
                call.name() + " " + abbreviate(call.arguments().toString())));

        ToolResult result;
        if ("deleteTask".equals(call.name()) && !highRiskApproved) {
            result = ToolResult.fail(HIGH_RISK_DENIED + ": " + call.arguments());
        } else {
            result = registry.execute(call);
        }

        trace.record(TraceEvent.of(runId, workerId, TraceEventType.TOOL_RESULT,
                (result.success() ? "OK " : "ERR ") + call.name() + " → " + abbreviate(result.message())));
        trace.recordTool(new TraceService.ToolEvent(runId, workerId, call.name(), args,
                result.success(), abbreviate(result.message()), System.currentTimeMillis() - t0,
                System.currentTimeMillis()));
        return result;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 160 ? s : s.substring(0, 157) + "...";
    }
}
```

要点：查表（`registry.execute`）是同进程 Map + 直接方法调用，耗时纳秒级；
高风险门禁（`highRiskApproved`）是执行器统一施加的。

### 框架版（本模块）

```java
@Override
public String call(String toolInput) {
    Map<String, Object> arguments = parseArguments(toolInput);
    try {
        String result = client.call(tool.name(), arguments);   // JSON-RPC 发到独立进程
        trace.record(RunTrace.Kind.MCP_TOOL_CALL, ...);
        return result;
    } catch (TaskMcpClient.McpException e) {
        trace.record(RunTrace.Kind.MCP_TOOL_CALL, ...);
        throw e;
    }
}
```

Server 端（`TaskMcpServer.toolsCall`）做真正的查表与执行，经管道返回文本。

### 差异本质

| 维度 | 手写 | 框架+MCP |
|---|---|---|
| 工具定义 | 编译时写死的 `Tool` 接口实现 | 运行时 `tools/list` 发现的 JSON Schema |
| 分派位置 | Client 进程内 `registry.execute` | Server 进程内 `toolsCall` |
| 调用耗时 | 纳秒级方法调用 | 毫秒级进程间请求 |
| 错误面 | 业务错误 + 高风险门禁 | 业务错误（返回字符串）+ 管道断开/进程崩溃/未知工具（抛异常） |
| 权限门禁 | `ToolExecutor` 统一门禁 | **本章未实现**（见下） |

### 取舍

- 得：工具可独立部署、独立升级；Client 换语言也行（协议是 JSON）。
- 失：调试要分两段查（Client 管道 vs Server 逻辑）；调用延迟高两个数量级；工具版本与 Client 期望的 Schema 可能漂移。
- 框架不管：权限。手写版 `ToolExecutor` 的 `highRiskApproved` 门禁在 MCP 版里没有对应物——`get_task_status` 只读所以没设防，换成写操作必须在 Server 端重建鉴权/审批。这是 Prompt 04 必答题第 4 题的答案。

### 实际运行证据

`McpBoundaryTest` 四个用例：schema 含 `task_id`、T-2 返回 BLOCKED、
未知任务返回 `task not found: T-99`（字符串，非异常，留给模型重试）、
缺参数抛 `McpException`（中断，由框架兜底）。

---

## 5. TRANSPORT — 新增的传输层：调工具要走管道 {#5-transport}

**类别**：`HTTP`
**框架版位置**：`spring-ai/advanced/src/main/java/com/example/springai/advanced/StdioProcessTransport.java`

> 与第三章的关系：无重合。第三章第 8 节讲的是"调模型不再手写网络请求"，本节讲的是"调工具要走进程管道"——
> 手写版和第三章里都没有这个概念，是本章全新内容。

### 手写版（agent-harness）

手写版没有这个概念。手写版唯一的远程调用是调模型（`OpenAiCompatibleLlmClient` 手写 HTTP），
工具全是同进程方法——"调个工具还要序列化、走管道、等响应"是本章第一次出现。

### 框架版（本模块）

```java
public synchronized String exchange(String requestLine) {
    if (!process.isAlive()) {
        throw new TaskMcpClient.McpException(
                "MCP server process died, exit=" + process.exitValue());
    }
    stdin.write(requestLine);
    stdin.newLine();
    stdin.flush();
    String response = stdout.readLine();
    if (response == null) {
        throw new TaskMcpClient.McpException("MCP server closed stdout");
    }
    return response;
}
```

子进程启停：`StdioProcessTransport.spawn()` fork `McpServerMain`，
`close()` 关管道 + `process.destroy()`。`JsonRpcTransport` 接口另有
`InProcessTransport` 实现（测试用，同协议不同边界）。

### 差异本质

| 维度 | 手写 | 本章 |
|---|---|---|
| 工具传输 | 无（同进程） | stdio 管道，一行 JSON 来、一行 JSON 回 |
| 新增故障 | 无 | 进程崩溃、管道断开、子进程起不来 |
| 并发 | 方法调用天然线程安全 | `exchange` 加锁，一次只飞一个请求（教学简化） |
| 协议与传输 | 不分家 | `JsonRpcTransport` 接口隔离，换传输不换协议 |

### 取舍

- 得：`McpBoundaryTest` 用 `InProcessTransport` 即可验证全部协议行为，
  证明"协议相同、边界不同"——边界只影响故障面，不影响工具语义。
- 失：stdio 一次一问，多工具并发要自己做多路复用；子进程日志混进 stderr 要另管。
- 框架不管：进程生命周期。本章 `Main` 手动 `spawn/close`，生产环境得用进程管家或改走 HTTP/SSE 传输。

---

## 6. TRACE — 轨迹：散落字符串 → 结构化事件 {#6-trace}

**类别**：`TRACE`
**框架版位置**：`spring-ai/advanced/src/main/java/com/example/springai/advanced/RunTrace.java`

> 与第三章的关系：第三章第 9 节回答的是"计划执行到第几步"（读数据库里的计划表）；
> 本节回答的是"这一次运行里检索和工具各发生在什么时候"（读内存里的事件列表）。一个看多步进度，一个看单次层次。

### 手写版（agent-harness）

`TraceService.record(TraceEvent.of(runId, workerId, type, message)`——散落在
`AgentRunner`、`ContextBuilder`、`ToolExecutor` 各处的字符串打点。
能回答"发生过什么"，回答不了"检索发生在第几次模型调用之前"（要人肉翻日志排序）。

### 框架版（本模块）

```java
public record Event(Kind kind, Instant at, String summary, Map<String, Object> attrs) { }
```

四种 `Kind` 与 Prompt 04 的 Part C 一一对应：
`MODEL_CALL / RETRIEVAL / MCP_TOOL_CALL / FINAL_RESPONSE`。
埋点在三处框架回调里：`AdvancedAgent.chat` 首尾、`RetrievalAdvisor.adviseCall`、
`McpTaskToolCallback.call`——注意埋点仍是手写代码，
框架只提供了"可插回调的位置"，没替你打点。

### 差异本质

| 维度 | 手写 | 本章 |
|---|---|---|
| 事件类型 | `TraceEventType` 字符串枚举，散 | `RunTrace.Kind` 四种，收敛到一次 run 的层次 |
| 顺序 | 靠日志时间戳人肉排序 | `events()` 列表顺序即因果顺序 |
| 属性 | 一句话 message | `summary` + 结构化 `attrs`（hitCount、ok、tool） |
| 输出 | 散落日志 | `format()` 树状打印，一眼看到层次 |

### 取舍

- 得：能直接回答本章三个观察问题（检索何时、工具何时、谁串起两者）。
- 失：仍是自研轻量实现，不是 Micrometer/OTel——跨进程追踪（MCP Server 内部耗时）
  目前看不见，Server 端要另加 traceId 透传才行。
- 框架不管：打点位置。`RunTrace` 全靠三个埋点自觉调用，漏埋一个就少一段。

### 实际运行证据

`RunTraceTest.recordsFourKindsInOrder`：四种事件按记录顺序收集；
`formatShowsRunIdAndKinds`：打印含 runId 与阶段名。真实 run 输出见模块 README。

---

## 本模块没有打标记的类

| 类 | 为什么不打 |
|---|---|
| `TaskStatus` / `TaskRecord` | 纯领域 POJO，无逻辑可对比 |
| `TaskRepository` | 内存 Map 存取，手写版 `TaskStore` 几乎一样，删掉它两边都要重写一份 |
| `ModelConfig` | 读 `.env` 的纯配置代码，框架不管（§5.5 明确排除） |
| `KeywordEmbeddingModel` | 桩实现，本身就是"不用框架时自己写的逻辑"，无对比可言 |
| `JsonRpcTransport` / `InProcessTransport` | 传输接口与测试替身，框架无对应物 |
| `TaskMcpServer` 的 `toolsList/toolsCall` 私有方法 | 已在类级 Javadoc 打过，不逐方法刷屏 |
| `TaskMcpClient` 的 `discover/call` | 类级已有说明；方法是 Client 的正常形态 |
| `McpServerMain` | 进程入口胶水（读 stdin 写 stdout 循环），无框架对应点 |
| `Main` | 装配块，类级 Javadoc 已有 `@FW-CMP` 说明 |
| 测试类（4 个） | 按规范不打（测试本身不是对比点，桩链/桩模型是常规测试手段） |

## 本章 `@FW-CMP` 标记清单

```bash
grep -rn "@FW-CMP" spring-ai/advanced/src
```

```text
spring-ai/advanced/src/main/java/com/example/springai/advanced/TaskMcpClient.java:14: * <p>@FW-CMP 本类在手写版里没有对应物——手写版的工具全是同进程方法，
spring-ai/advanced/src/main/java/com/example/springai/advanced/StdioProcessTransport.java:15: * <p>@FW-CMP [HTTP] 传输边界：手写版 vs 远程工具
spring-ai/advanced/src/main/java/com/example/springai/advanced/McpTaskToolCallback.java:13: * <p>@FW-CMP 本类对应手写版 {@code agent-harness/Tool}（工具定义接口）。
spring-ai/advanced/src/main/java/com/example/springai/advanced/McpTaskToolCallback.java:47:    // @FW-CMP [TOOL_DISPATCH] 工具分派：本地反射调用 → 远程 JSON-RPC 调用
spring-ai/advanced/src/main/java/com/example/springai/advanced/AdvancedAgent.java:12: * <p>@FW-CMP 本类对应手写版 {@code agent-harness/AgentRunner}（循环编排器）。
spring-ai/advanced/src/main/java/com/example/springai/advanced/AdvancedAgent.java:55:        // @FW-CMP [TOOL_LOOP] 多轮工具循环的归属（RAG + MCP 版）
spring-ai/advanced/src/main/java/com/example/springai/advanced/Main.java:16: * <p>@FW-CMP 本类对应手写版 {@code agent-harness/Main}（装配 + 启动）。
spring-ai/advanced/src/main/java/com/example/springai/advanced/RetrievalAdvisor.java:17: * <p>@FW-CMP 本类对应手写版 {@code agent-harness/ContextBuilder}（上下文组装器）。
spring-ai/advanced/src/main/java/com/example/springai/advanced/RetrievalAdvisor.java:50:    // @FW-CMP [CONTEXT] 检索结果进入 Context 的时机与位置
spring-ai/advanced/src/main/java/com/example/springai/advanced/PolicyKnowledgeBase.java:19: * <p>@FW-CMP 本类对应手写版 {@code agent-harness/KnowledgeStore}（本地知识库）。
spring-ai/advanced/src/main/java/com/example/springai/advanced/PolicyKnowledgeBase.java:61:        // @FW-CMP [MEMORY] 知识入库：手写版 vs 框架版
spring-ai/advanced/src/main/java/com/example/springai/advanced/RunTrace.java:13: * <p>@FW-CMP [TRACE] 本类对应手写版 {@code agent-harness/TraceService + TraceEvent}（打点记录器）。
spring-ai/advanced/src/main/java/com/example/springai/advanced/TaskMcpServer.java:11: * <p>@FW-CMP 本类对应手写版 {@code agent-harness/ToolRegistry + ToolExecutor}（工具查表与执行）。
```

共 13 处标记，6 个类别全覆盖导航表。

## 本章刻意没做什么

Long-term Memory（用户偏好类记忆，非知识检索）、Planning、Multi-Agent、Checkpoint、
HITL 人工审批（手写版 `ToolExecutor.highRiskApproved` 在 MCP 版无对应实现，见 §4）、
真实 Embedding 模型、真实第三方 MCP Server、Micrometer/OpenTelemetry 接入、
RAG 重排与混合检索、MCP prompts/resources/roots 能力、Spring Boot 集成。
