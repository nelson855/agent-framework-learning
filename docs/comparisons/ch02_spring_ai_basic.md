# Ch02 Spring AI 核心抽象 — 手写 vs 框架 完整对比

> 本文件是 **第 2 章** 的完整对比文档，对应代码模块 `spring-ai/basic`。
> 代码里所有差异点都打了 `@FW-CMP` 标记；每个标记末尾有 `#N-xxx` 锚点指向本文档对应章节。
>
> 全局搜索：
>
> ```bash
> grep -rn "@FW-CMP" spring-ai/basic/src
> ```
>
> 按类别过滤：
>
> ```bash
> grep -rn "@FW-CMP \[TOOL_LOOP\]" spring-ai/basic/src
> ```

## 本章节差异点导航

| # | 类别 | 位置（框架版） | 一句话差异 |
|---|---|---|---|
| 1 | `PROMPT` | [TaskAgent.SYSTEM_PROMPT](../../spring-ai/basic/src/main/java/com/example/springai/basic/TaskAgent.java) | 流程顺序：代码 → 提示词 |
| 2 | `TOOL_LOOP` | [TaskAgent.chat()](../../spring-ai/basic/src/main/java/com/example/springai/basic/TaskAgent.java) | 多轮工具循环：手写 for/if → 框架 Advisor |
| 3 | `HTTP` | [Main.main](../../spring-ai/basic/src/main/java/com/example/springai/basic/Main.java) | HTTP 客户端与序列化：手写 → 框架隐藏 |
| 4 | `SCHEMA` | [TaskTools.createTask](../../spring-ai/basic/src/main/java/com/example/springai/basic/TaskTools.java) | 工具 schema：手写 ToolDefinition → 注解反射 |
| 5 | `ERROR_SEMANTIC` | [TaskTools.getTask](../../spring-ai/basic/src/main/java/com/example/springai/basic/TaskTools.java) | 错误回馈：控制流 → 提示词工程 |

---

## 1. PROMPT — 流程顺序：代码 → 提示词 {#1-prompt}

**类别**：`PROMPT`
**框架版位置**：[TaskAgent.java](../../spring-ai/basic/src/main/java/com/example/springai/basic/TaskAgent.java) `SYSTEM_PROMPT` 字段上方

### 手写版（agent-harness）

手写版没有"系统提示词承载流程"这一说。流程靠 `Planner` 拆成 `PlanStep` 列表，`AgentRunner` 逐步推进：

```java
// agent-harness/Main.java
String goal = "阅读本地规范，创建两个子任务，按规范删除一个高风险任务，" +
              "再交给 review-worker 核对，最后总结并评估";
AgentRunner.RunResult result = svc.run(goal);

// agent-harness/AgentRunner.run(String goal)
public RunResult run(String goal) {
    String runId = "run-" + System.currentTimeMillis();
    List<PlanStep> plan = svc.planner.plan(goal);              // 计划由 Planner 拆
    svc.runStore.create(runId, goal, svc.planner.toJson(plan), ...);
    List<Message> context = svc.contextBuilder.build(runId, null, goal, plan, "目标: " + goal);
    int cp = svc.checkpoint.save(runId, 0, context);
    RunResult result = loop(runId, context, plan, 0);          // 循环推进
    ...
}
```

**关键点**：执行顺序由 **Java 代码结构**（`Planner` 拆计划 + `AgentRunner` 的 for 循环）约束。

### 框架版（spring-ai/basic）

```java
// spring-ai/basic/TaskAgent.java
private static final String SYSTEM_PROMPT = """
        你是一个任务助手，帮用户管理学习任务。
        创建任务用 createTask 工具，查询任务用 getTask 工具。
        创建完任务后，再查一次该任务，把任务编号和状态告诉用户。
        用和用户相同的语言回答。不要透露你的思考过程。
        """;
```

执行顺序写在自然语言里（"创建完任务后，再查一次"），由模型按提示自主决定。

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 载体 | Java 代码（Planner + 循环） | 提示词（自然语言） |
| 修改方式 | 改代码、重新编译 | 改这段话就行 |
| 约束力 | 编译器保证 | 模型"听话"程度保证 |
| 可见性 | 静态可读 | 运行时才体现 |

### 取舍

- **得**：流程改动不动代码，改提示词即可
- **失**：模型可能不听话；要靠提示词工程 + 日志观察验证
- **框架不管**：提示词写得好不好，框架不管

---

## 2. TOOL_LOOP — 多轮工具循环的归属 {#2-tool-loop}

**类别**：`TOOL_LOOP`
**框架版位置**：[TaskAgent.chat()](../../spring-ai/basic/src/main/java/com/example/springai/basic/TaskAgent.java) 内的 fluent 调用上方

### 手写版（agent-harness）

完整循环在 `AgentRunner.loop`，约 50 行（含 checkpoint/trace/审批分支）：

```java
// agent-harness/AgentRunner.java
private RunResult loop(String runId, List<Message> context, List<PlanStep> plan, int stepFrom) {
    int memoryCount = 0;
    for (int step = stepFrom; step < maxSteps; step++) {
        memoryCount = svc.contextBuilder.injectMemory(context, runId, null,
                svc.goalOf(runId), memoryCount);

        String reply = svc.model.chat(runId, null, context);   // ① 调模型
        AgentDecision decision = AgentDecisionParser.parse(reply); // ② 解析返回

        if (decision.isFinal()) {
            svc.runStore.update(runId, RunStatus.RUNNING, step + 1);
            return RunResult.completed(decision.answer());      // ③ 终止判断
        }

        ToolCall call = decision.toolCall();

        // HITL：deleteTask 必须先过人工审批
        if ("deleteTask".equals(call.name())) {
            context.add(Message.assistant(reply));
            int cp = svc.checkpoint.save(runId, step + 1, context);
            int approvalId = svc.approval.create(runId, call,
                    "Agent 请求删除任务: " + call.arguments(), "HIGH");
            svc.runStore.update(runId, RunStatus.WAITING_APPROVAL, step + 1);
            return RunResult.waiting(approvalId);
        }

        // Multi-Agent：delegateTo 由 Orchestrator 接管
        ToolResult result;
        if ("delegateTo".equals(call.name())) {
            result = svc.orchestrator.delegate(runId, strArg(call, "worker"), strArg(call, "task"));
        } else {
            result = svc.toolExec.execute(call, runId, null, false); // ④ 执行工具
        }

        context.add(Message.assistant(reply));                  // ⑤ 手动回填助手消息
        context.add(Message.user(observation(call, result)));   // ⑥ 手动回填工具结果
        svc.trace.record(TraceEvent.of(runId, null, TraceEventType.STATE_CHANGED,
                "context→" + context.size()));
        int cp = svc.checkpoint.save(runId, step + 1, context);
        svc.runStore.update(runId, RunStatus.RUNNING, step + 1);
    }
    svc.runStore.update(runId, RunStatus.MAX_STEP_EXCEEDED, maxSteps);
    return RunResult.maxStepsExceeded();
}
```

每一步都要手动：调模型 → 解析 → 判终止 → 执行工具 → 回填上下文 → 存 checkpoint → 记 trace。

### 框架版（spring-ai/basic）

```java
// spring-ai/basic/TaskAgent.java
public AgentResult chat(String userInput) {
    toolCalls.clear();
    String finalAnswer = ChatClient.create(chatModel)
            .prompt()
            .system(SYSTEM_PROMPT)
            .user(userInput)
            .tools(taskTools)
            .call()                                            // 就这一次
            .content();
    return new AgentResult(userInput, List.copyOf(toolCalls), finalAnswer);
}
```

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 代码量 | ~50 行（`AgentRunner.loop`） | 1 次 `.call()` |
| 循环主体 | 业务代码 for 循环 | `ToolCallingAdvisor` 递归重入 Advisor 链 |
| 上下文回填 | `context.add(...)` 手动 | 框架把工具结果作为 tool message 追加 |
| 终止判断 | `decision.isFinal()` 显式 | "模型某轮不再发起 tool_call" 隐式 |
| 上限 | `maxSteps=12` 显式常量 | 2.0.0 无可配上限（2.0.1 才有 `maxToolCalls`） |
| Checkpoint | 每步 `svc.checkpoint.save(...)` | **本章未实现** |
| HITL 审批 | deleteTask 显式分支 | **本章未实现** |
| Multi-Agent 分派 | `delegateTo` 显式分支 | **本章未实现** |

### 取舍

- **得**：业务代码里"循环、解析、回填"全部消失；测试时单跑一次就能验证
- **失**：循环过程对业务代码不可见，只能从 `[TOOL]` 日志反推；没有内置 checkpoint / HITL / 审批
- **本章刻意没做**：checkpoint、HITL、Multi-Agent（留给后续章节）

### 实际运行证据

参见 [spring-ai/basic/README.md 实际调用序列](../../spring-ai/basic/README.md)：

```text
model turn 1: user input -> 创建一个"学习 Spring AI Advisor"的任务...
model turn 2: -> createTask(...) -> tool result: 已创建任务 T-1 ...
model turn 3: -> getTask(taskId=T-1) -> tool result: 任务 T-1 ...
model turn 4: -> final answer: 任务已创建并查询完成 ...
```

代码里只有 **1 次** `.call()`，实际走了 **4 轮**。

---

## 3. HTTP — HTTP 客户端与序列化 {#3-http}

**类别**：`HTTP`
**框架版位置**：[Main.java](../../spring-ai/basic/src/main/java/com/example/springai/basic/Main.java) 内 `ChatModel` 装配块上方

### 手写版（agent-harness）

完整版在 `OpenAiCompatibleLlmClient.chat()`，手动拼请求、发 HTTP、解析 JSON：

```java
// agent-harness/OpenAiCompatibleLlmClient.java
@Override
public LlmResponse chat(List<Message> messages) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("messages", messages);
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .build();
    HttpResponse<String> response = send(request);
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + response.body());
    }
    JsonNode root = parse(response.body());
    return new LlmResponse(root.at("/choices/0/message/content").asText());
}
```

注意：**这个版本只处理了 content，没处理 tool_calls**——手写版的"工具调用"其实是让模型输出一段 JSON 文本，再用 `AgentDecisionParser` 从文本里抠出来（见下方 SCHEMA 对比）。

### 框架版（spring-ai/basic）

```java
// spring-ai/basic/Main.java
ChatModel chatModel = OpenAiChatModel.builder()
        .openAiClient(OpenAIOkHttpClient.builder()
                .baseUrl(config.baseUrl())
                .credential(BearerTokenCredential.create(config.apiKey()))
                .timeout(config.timeout())
                .build())
        .openAiClientAsync(OpenAIOkHttpClientAsync.builder()
                .baseUrl(config.baseUrl())
                .credential(BearerTokenCredential.create(config.apiKey()))
                .timeout(config.timeout())
                .build())
        .options(OpenAiChatOptions.builder().model(config.model()).build())
        .build();
```

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| HTTP 客户端 | JDK `HttpClient` 直接用 | OpenAI Java SDK（`openai-java`）封装 |
| 请求体拼装 | `Map<String, Object>` 手动 put | SDK 内部 |
| 工具 schema 序列化 | 不序列化（走文本约定） | SDK + Spring AI 反射生成 |
| 响应解析 | JSON path 抠 `/choices/0/message/content` | SDK 返回结构化对象 |
| 同步/异步 | 只有同步 | Spring AI 2.0 **强制两个都给** |
| 依赖 | 纯 JDK + Jackson | `openai-java-core` + `openai-java-client-okhttp` |

### 取舍

- **得**：不再手写 HTTP / JSON；对 OpenAI 协议变更免疫（SDK 跟着升）
- **失**：引入 OpenAI Java SDK 依赖，换厂商要换 SDK；同步 + 异步两个客户端都要配（2.0 的硬性要求）

---

## 4. SCHEMA — 工具 schema 的生成方式 {#4-schema}

**类别**：`SCHEMA`
**框架版位置**：[TaskTools.createTask](../../spring-ai/basic/src/main/java/com/example/springai/basic/TaskTools.java) 方法注解上方

### 手写版（agent-harness）

手写版有三个类协作：

```java
// agent-harness/ToolDefinition.java
public record ToolDefinition(String name, String description,
                             Map<String, String> parameters) {}

// agent-harness/Tool.java（接口）
public interface Tool {
    ToolDefinition definition();
    ToolResult execute(Map<String, Object> arguments);
}

// agent-harness/ToolRegistry.java
public final class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.definition().name(), tool);
        return this;
    }

    /** 生成给模型看的工具说明（JSON 数组文本），写入 system prompt。 */
    public String toolsInstruction() {
        return objectMapper.writeValueAsString(definitions());
    }

    public ToolResult execute(ToolCall call) {
        Tool tool = tools.get(call.name());
        if (tool == null) return ToolResult.fail("UNKNOWN_TOOL: " + call.name());
        String error = ArgumentValidator.validate(tool.definition(), call.arguments());
        if (error != null) return ToolResult.fail("参数校验失败: " + error);
        return tool.execute(call.arguments());
    }
}
```

每个工具要：
1. 写 `Tool` 接口实现类
2. 手动 `new ToolDefinition("createTask", "...", Map.of("title", "string"))`
3. 注册进 `ToolRegistry`
4. `ToolRegistry.toolsInstruction()` 序列化成 JSON 塞进系统提示词
5. `AgentDecisionParser` 从模型输出里抠 `{type:"tool_call", tool:"...", arguments:{...}}`
6. `ToolRegistry.execute` 查表 → 校验参数 → 调用 `tool.execute(...)` → 返回 `ToolResult`

### 框架版（spring-ai/basic）

```java
// spring-ai/basic/TaskTools.java
@Tool(name = "createTask", description = "用给定的标题创建一个新的学习任务。返回新任务的编号和状态。")
public String createTask(
        @ToolParam(description = "要创建的任务标题，例如：学习 Spring AI Advisor") String title) {
    Task task = taskService.createTask(title);
    return "已创建任务 " + task.id() + "，标题=" + task.title() + "，状态=" + task.status();
}
```

然后在 `TaskAgent.chat()` 里 `.tools(taskTools)` 一挂，全部完成。

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 工具定义类 | `Tool` 接口 + `ToolDefinition` record | 普通 Java 方法 + `@Tool` 注解 |
| Schema 生成 | 手动 `Map.of("title", "string")` | 框架反射读签名 |
| Schema 注入提示词 | `ToolRegistry.toolsInstruction()` 手动拼 | 框架自动随请求发送 |
| 工具分派 | `ToolRegistry.execute(call)` 查表 | 框架反射调方法 |
| 参数校验 | `ArgumentValidator.validate` 手写 | 框架按 schema 校验 |
| 模型输出解析 | `AgentDecisionParser` 从文本抠 JSON | 框架读标准 `tool_calls` 字段 |

### 消失的三个类

- `Tool.java`（接口）→ 删除
- `ToolDefinition.java` → 删除（注解取代）
- `ToolRegistry.java` → 删除（框架内部 ToolCallingManager 取代）
- `AgentDecisionParser.java` → 删除（模型直接返回结构化 tool_calls）

### 取舍

- **得**：四个类消失；新增工具只需要写一个 `@Tool` 方法
- **失**：方法签名变成"给模型看的说明书"——名字、参数名、描述写不好，模型选不准；这些"质量"框架不检查

---

## 5. ERROR_SEMANTIC — 错误回馈：控制流 → 提示词工程 {#5-error-semantic}

**类别**：`ERROR_SEMANTIC`
**框架版位置**：[TaskTools.getTask](../../spring-ai/basic/src/main/java/com/example/springai/basic/TaskTools.java) `.orElse(...)` 上方

### 手写版（agent-harness）

工具失败走 `ToolResult.fail`，由 `AgentRunner` 决定下一步：

```java
// agent-harness/ToolRegistry.java
public ToolResult execute(ToolCall call) {
    Tool tool = tools.get(call.name());
    if (tool == null) {
        return ToolResult.fail(UNKNOWN_TOOL + ": " + call.name());
    }
    ...
}

// agent-harness/AgentRunner.loop（错误传播到处置）
ToolResult result = svc.toolExec.execute(call, runId, null, false);
context.add(Message.user(observation(call, result)));   // 失败也塞回上下文
// 由模型下一轮自己看到失败消息，决定重试还是放弃
```

### 框架版（spring-ai/basic）

```java
// spring-ai/basic/TaskTools.java
@Tool(name = "getTask", description = "按编号查询任务。...")
public String getTask(@ToolParam(description = "...") String taskId) {
    String result = taskService.getTask(taskId)
            .map(task -> "任务 " + task.id() + "，...")
            .orElse("找不到任务：" + taskId);     // ← 返回一句话而不是抛异常
    trace("getTask", "taskId=" + taskId, result);
    return result;
}
```

### 差异本质

手写版和框架版**最终都是**"把错误信息塞回上下文让模型看到"，但**决策路径**不同：

| 维度 | 手写 | 框架 |
|---|---|---|
| 错误表征 | `ToolResult.fail(...)` 显式类型 | 普通字符串（语义约定） |
| 谁决定"塞回上下文" | `AgentRunner` 控制流 | 框架自动 |
| 业务要不要区分错误类型 | 要（`ToolResult.success/fail`） | 不用（都返回 String） |
| 重试决策 | 模型看到失败 → 决定重试 | 模型看到字符串 → 决定重试 |

### 为什么"返回一句话而不是抛异常"

如果 `getTask` 抛异常：
- 框架会把异常堆栈当工具错误返回给模型，模型看到的是 stack trace 不是友好提示
- 业务代码也会被迫处理 try/catch 控制流

返回一句话 `"找不到任务：T-1"`：
- 框架把它当正常工具结果回填
- 模型读得懂"找不到"，可以换参数（比如 `T-2`）再试

**这是业务语义**，框架不会替你想"这里该抛还是该返回"。写 `@Tool` 方法时要主动决定。

---

## 本模块没有打标记的类（框架不管的部分）

以下类 **故意没有** `@FW-CMP` 标记——它们属于"框架不接管、手写也这样写"的业务代码：

- `TaskService` — 业务规则（校验、组合）
- `TaskRepository` — 持久化（SQLite）
- `Task` / `TaskStatus` — 领域模型
- `AgentResult` / `ToolCallTrace` — 结果收集的 POJO
- `ModelConfig` — 读 `.env` 的工具类（Spring AI 不管配置加载）

**框架只接管"流程"，不接管"业务"**——这是本章最重要的心智模型。

---

## 本章 `@FW-CMP` 标记清单

```bash
$ grep -rn "@FW-CMP" spring-ai/basic/src/main/java
```

- `TaskAgent.java` 类级 Javadoc — 整体说明（对应 `AgentRunner`）
- `TaskAgent.java` `SYSTEM_PROMPT` 上方 — `[PROMPT]` 流程顺序
- `TaskAgent.java` `chat()` fluent 调用上方 — `[TOOL_LOOP]` 工具循环
- `Main.java` 类级 Javadoc — 整体说明（对应 `agent-harness/Main`）
- `Main.java` `ChatModel` 装配块上方 — `[HTTP]` HTTP 客户端
- `TaskTools.java` 类级 Javadoc — 整体说明（对应 `Tool`/`ToolDefinition`/`ToolRegistry`）
- `TaskTools.java` `createTask` 上方 — `[SCHEMA]` 工具 schema
- `TaskTools.java` `getTask` 内 `.orElse(...)` 上方 — `[ERROR_SEMANTIC]` 错误语义
