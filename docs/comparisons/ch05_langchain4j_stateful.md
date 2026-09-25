# Ch05 LangChain4j Stateful Agent — 手写 vs 框架 完整对比

> 本文件是 **第 5 章** 的完整对比文档，对应代码模块 `langchain4j/stateful-agent`。
> 代码里所有差异点都打了 `@FW-CMP` 标记；每个标记末尾有 `#x-xxx` 字符串指向本文档对应章节（全文搜索即可定位）。
> Spring AI 版（Ch03）的同业务实现见 `spring-ai/stateful-agent`，本文档会同时标注"和 Spring AI 版的区别"。
>
> 全局搜索：
>
> ```bash
> grep -rn "@FW-CMP" langchain4j/stateful-agent/src
> ```
>
> 按类别过滤：
>
> ```bash
> grep -rn "@FW-CMP \[TOOL_LOOP\]" langchain4j/stateful-agent/src
> ```

## 本章节差异点导航

| # | 类别 | 位置（框架版） | 一句话差异 |
|---|---|---|---|
| 1 | `PROMPT` | [StatefulAssistant](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/StatefulAssistant.java) | 提示词从"背说明书 + JSON 协议"瘦成"角色一句话"，且搬到接口注解上 |
| 2 | `CONTEXT` | [TaskAgentService（类级）](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskAgentService.java) | 上下文从"自己抱着的对象"变成"代理内部维护 + 调前手工拼记忆" |
| 3 | `TOOL_LOOP` 内层 | [TaskAgentService 构造器](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskAgentService.java) | `executeStep` 循环 + 解析器 → 代理一次 `chat()` |
| 4 | `TOOL_LOOP` 外层 + `STATE` | [PlanExecutor（类级）](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/PlanExecutor.java) / [PlanRepository（类级）](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/domain/PlanRepository.java) | 外层计划推进框架没有概念，自建；状态表原样自建 |
| 5 | `SCHEMA` 工具 | [TaskTools.createTask](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskTools.java) | 工具说明书：手写 ToolDefinition → `@Tool`/`@P` 注解反射 |
| 6 | `SCHEMA` 计划 | [Planner](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/Planner.java) / [Replanner](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/Replanner.java) | 计划 JSON 解析：手写 PlanParser → 手工 Jackson（刻意对标，见取舍） |
| 7 | `MEMORY` | [MemoryService（类级）](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/MemoryService.java) / [TaskAgentService.memoryFor](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskAgentService.java) | 短期窗口框架包（按会话隔离），长期记忆连存带取带注入全自建 |
| 8 | `HTTP` + `CONFIG` | [ChatModelFactory（类级）](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/config/ChatModelFactory.java) / [StatefulAgentApp（类级）](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/StatefulAgentApp.java) | 手写 HTTP 客户端消失；装配仍手写 |
| 9 | `CONFIG` Web + `TRACE` | [WebServer（类级）](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/web/WebServer.java) | Web 层沿用手写版 JDK HttpServer；工具调用靠方法内记账补回可见性 |

---

## 1. PROMPT — 提示词只讲"角色"，搬到接口注解上 {#1-prompt}

**类别**：`PROMPT`
**框架版位置**：[StatefulAssistant.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/StatefulAssistant.java) `chat()` 方法上方

### 手写版（stage02 `StatefulAgentRunner.systemPrompt`，完整方法）

```java
// references/handwritten-agent-v1/stateful-agent/.../stage02/StatefulAgentRunner.java
private String systemPrompt() {
    return """
            你是一个有状态的控制台 AI 任务助手。你可以调用工具完成用户的任务。
            当前可用的工具（JSON 数组描述）：
            %s

            决策协议（只输出一个 JSON，不要输出任何其他文字）：
            1. 需要调用工具时：
               {"type":"tool_call","tool":"工具名","arguments":{参数名:值,...},"decisionSummary":"这一步为什么这么做，一句话"}
            2. 当前计划步骤的任务已完成、可以回答用户时：
               {"type":"final","answer":"给用户的最终回答"}

            对话中带 [observation] 前缀的消息是工具执行后的观察结果，请基于它继续决策；
            不要重复执行一个已经执行过、且结果已知的工具调用。
            带 [RETRIEVED MEMORY] 前缀的是从长期记忆中检索到的用户信息，回答时应当尊重这些偏好。
            带"当前执行计划步骤"的是你正在执行的一步计划，请完成它并给出 final。
            如果某个工具调用失败，请调整你的方案重新尝试。
            """.formatted(tools.toolsInstruction());
}
```

**关键点**：提示词一个人背三件事——角色、工具说明书（`toolsInstruction()` 动态拼进去）、输出格式协议（因为 `AgentDecisionParser` 只能从纯文本里抠 JSON）。

### 框架版（`langchain4j/stateful-agent`）

```java
// langchain4j/stateful-agent/.../agent/StatefulAssistant.java
@SystemMessage("""
        你是任务管理助手。用户想创建任务就调用 createTask，
        想查询任务就调用 getTask，想更新状态就调用 updateTaskStatus。
        回答用中文，简短直接。
        """)
String chat(@MemoryId String memoryId, @UserMessage String message);
```

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 工具说明书 | 提示词里动态拼接 JSON | 注解反射生成 schema，随请求走标准 `tools` 字段 |
| 输出协议 | 提示词约定 JSON，解析器抠 | 模型原生 `tool_calls`，无文本可抠 |
| 存放位置 | Runner 私有方法 | 接口方法注解（声明式，离调用点最近） |
| Spring AI 版 | 同样瘦提示词，放服务类字符串常量 | 放接口注解，位置不同性质相同 |

### 取舍

- 得：提示词短到只剩角色；工具增删不用改提示词。
- 失：提示词藏进注解，调试时"模型到底看到什么"要靠日志反推。
- 框架不管：提示词写得好不好（工具选得准不准），仍是业务责任。

---

## 2. CONTEXT — 上下文从对象变成代理内部状态 {#2-context}

**类别**：`CONTEXT`
**框架版位置**：[TaskAgentService.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskAgentService.java) 类级 Javadoc

### 手写版（stage02 `StatefulAgentRunner.buildContext`，完整方法）

```java
private AgentContext buildContext(String conversationId, List<Memory> retrieved) {
    AgentContext context = new AgentContext();
    context.addSystem(systemPrompt());

    if (!retrieved.isEmpty()) {
        StringBuilder block = new StringBuilder("[RETRIEVED MEMORY]\n");
        for (Memory memory : retrieved) {
            block.append("- [").append(memory.type()).append("] ").append(memory.content()).append('\n');
        }
        context.addSystem(block.toString().stripTrailing());
    }

    for (StoredMessage stored : messages.findUserAndAssistantByConversation(conversationId)) {
        if ("user".equals(stored.role())) {
            context.addUser(stored.content());
        } else if ("assistant".equals(stored.role())) {
            context.addAssistant(stored.content());
        }
    }
    return context;
}
```

### 框架版（`langchain4j/stateful-agent`）

```java
// TaskAgentService.chat：没有 Context 对象了
messageRepository.appendMessage(conversationId, "user", userText);   // 流水账照记
String augmented = withMemories(userId, userText);                   // 长期记忆手工拼前缀
String answer = assistant.chat(conversationId, augmented);           // 历史 + 工具回填全在代理里
messageRepository.appendMessage(conversationId, "assistant", answer);
```

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 历史来源 | 每次查库全量拼 | 窗口在代理里自动维护 |
| 工具结果回填 | `context.addObservation(...)` 手工拼 | 代理自动回填 |
| 长期记忆 | 拼进 Context 对象 | 调代理前拼字符串前缀 |
| Spring AI 版 | 两块各归一个顾问（历史顾问 + 注入顾问） | 无顾问链，注入裸露在调用处 |

### 取舍

- 得：没有 Context 类了，`chat()` 四行讲完。
- 失：上下文不可见——"模型实际看到什么"要靠 `inspectWindow` 另查。
- 框架不管：记忆"查什么、拼什么"，仍是业务代码（`withMemories`）。

---

## 3. TOOL_LOOP 内层 — 循环加解析换成一次代理调用 {#3-tool-loop-inner}

**类别**：`TOOL_LOOP`
**框架版位置**：[TaskAgentService.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskAgentService.java) 构造器 `AiServices.builder(...)` 上方

### 手写版（stage02 `StatefulAgentRunner.executeStep`，完整方法）

```java
private StepOutcome executeStep(AgentContext context, AgentRun run, PlanStep step) {
    for (int i = 0; i < MAX_STEPS_PER_STEP; i++) {
        LlmResponse reply = llm.chat(context.messages());
        AgentDecision decision = AgentDecisionParser.parse(reply.content());

        if (decision.isFinal()) {
            return StepOutcome.done(decision.answer());
        }

        ToolCall call = decision.toolCall();
        run = runs.updateStatus(run.runId(), RunStatus.WAITING_TOOL, planStepIndex(step));

        ToolResult result = tools.execute(call);
        context.addAssistant(reply.content());
        context.addObservation(call, result, result.success());

        if (!result.success()) {
            return StepOutcome.failed(result.message());
        }
    }
    return StepOutcome.failed("单步 ReAct 循环超过最大步数 " + MAX_STEPS_PER_STEP);
}
```

外加 `AgentDecisionParser.parse`（约 50 行，从纯文本抠 `tool_call`/`final`，见上文阅读记录）。

### 框架版（`langchain4j/stateful-agent`）

```java
this.assistant = AiServices.builder(StatefulAssistant.class)
        .chatModel(chatModel)
        .chatMemoryProvider(this::memoryFor)
        .tools(taskTools)
        .build();
// 调用处：assistant.chat(conversationId, augmented);
```

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 循环 | `for` 循环自己写 | 代理内循环，模型给 `tool_calls` 就继续 |
| 分派 | `ToolRegistry.execute` 查表 | 代理按注解反射分派 |
| 解析 | `AgentDecisionParser` 抠 JSON | 无（原生 `tool_calls`，类整个删掉） |
| Spring AI 版 | `ChatClient` 一次 `.call()`（`ToolCallingAdvisor` 管循环） | 代理一次 `chat()`，两家藏的是同一件事 |

### 取舍

- 得：约 80 行循环加解析消失。
- 失：循环细节（重试几次、超限怎么办）藏进代理；"调了几次工具"看不见，靠 `TaskTools.recentCalls` 记账补回。
- 框架不管：工具做错事的后果（建错任务、改错状态），仍是业务责任。

### 实际运行证据

`PlanExecutorReplanTest.replansOnceThenSucceeds`：第一次模型调用抛异常，
外层捕获后重规划并成功，`plans.findByRunId(runId)` 为 2 份计划；
`TaskToolsTest.recordsRecentCallsForDebugger`：工具直调被记账，可被调试台读取。

---

## 4. TOOL_LOOP 外层 + STATE — 计划循环和状态表自建 {#4-plan-state}

**类别**：`TOOL_LOOP` / `STATE`
**框架版位置**：[PlanExecutor.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/PlanExecutor.java) 类级 Javadoc，[PlanRepository.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/domain/PlanRepository.java) 类级 Javadoc

### 手写版（stage02 `StatefulAgentRunner.run`，完整方法见上文阅读记录）

外层 `while (idx < plan.steps().size())`：推进步骤 → 调内层 `executeStep` →
失败则 `replan` 出新计划 → 状态落库；`plan`/`plan_step`/`agent_run` 三张表。

### 框架版（`langchain4j/stateful-agent`）

`PlanExecutor.execute`：建运行记录 → 拆计划 → 逐步执行 → 状态落库，
骨架与手写版几乎一样（见模块源码）；`PlanRepository` 表结构与 Spring AI 版一致。

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 外层循环 | 自己写 | 自己写（框架无"计划"概念） |
| 状态表 | 自己写 | 原样自建，一字不改的语义 |
| **本章未实现** | `SKIPPED` 状态 | 只有 PENDING/RUNNING/DONE/FAILED，与 Spring AI 版口径一致 |

### 取舍

- 本章最重要的分界线：内层代理管，外层自己写。两家框架在此完全一致。
- 如果不用框架，需要重新实现的是内层（§3），外层反正都要自己写。

---

## 5. SCHEMA 工具 — 注解反射替代手写说明书 {#5-schema}

**类别**：`SCHEMA`
**框架版位置**：[TaskTools.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskTools.java) `createTask` 上方

### 手写版（stage02 `ToolRegistry`，完整关键方法）

```java
public ToolResult execute(ToolCall call) {
    Tool tool = tools.get(call.name());
    if (tool == null) {
        return ToolResult.fail(UNKNOWN_TOOL + ": " + call.name());
    }
    String error = ArgumentValidator.validate(tool.definition(), call.arguments());
    if (error != null) {
        return ToolResult.fail("参数校验失败: " + error);
    }
    return tool.execute(call.arguments());
}

public String toolsInstruction() {
    try {
        return objectMapper.writeValueAsString(definitions());
    } catch (JsonProcessingException e) {
        throw new IllegalStateException("序列化工具说明失败", e);
    }
}
```

### 框架版（`langchain4j/stateful-agent`）

```java
@Tool("用给定的标题创建一个新的学习任务。返回新任务的编号。")
public String createTask(@P("要创建的任务标题，例如：学习 LangChain4j 记忆") String title) {
```

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| schema 来源 | 手写 `ToolDefinition` + 序列化 | 反射方法签名 + 注解 |
| 参数校验 | `ArgumentValidator` 显式校验 | 框架按 schema 校验，错误回给模型 |
| 注解名 | — | LangChain4j `@P` vs Spring AI `@ToolParam`，语义一样 |

### 取舍

- 得：工具说明书不用手写，增减参数改签名即可。
- 失：真实踩坑——没开 `maven-compiler-plugin` 的 `parameters=true`，
  参数名在运行时是 `arg0`，模型看到的说明书就是乱的（框架只告警不报错）。
  本模块 pom 里已钉住该开关。
- 框架不管：名字和描述写得好不好，直接决定模型选得准不准，仍是业务责任。

---

## 6. SCHEMA 计划 — 手工解析（刻意对标） {#6-schema-plan}

**类别**：`SCHEMA`
**框架版位置**：[Planner.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/Planner.java) 类级 Javadoc，[Replanner.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/Replanner.java) 类级 Javadoc

### 手写版

`PlanParser`：从模型纯文本抠 `{"steps":[...]}`（逻辑与 `AgentDecisionParser` 同类）。

### 框架版（`langchain4j/stateful-agent`）

```java
String content = chatModel.chat(
        "把用户的目标拆成具体的执行步骤，只输出 JSON，如 {\"steps\":[\"第一步\",\"第二步\"]}。"
                + "不要输出其他文字。\n目标：" + goal);
return parseSteps(content);   // Jackson 手工解析 + 容错兜底
```

### 差异本质

| 维度 | 手写 | 本模块 | Spring AI 版 |
|---|---|---|---|
| 解析 | 手写 parser | 手工 Jackson（一样） | `BeanOutputConverter`（框架生成格式约束） |
| 格式约束 | 无 | 提示词一句话 | 转换器生成的格式说明 |

### 取舍

- 刻意没用 AiServices 结构化输出：同一模块同时展示"声明式"（§3 的代理）
  和"底层直调"（本类）两种用法，对比价值大于省的那几行解析代码。
- 重规划同样无专用 API：什么算失败、几次算完、失败原因写回哪张表，全是业务代码。

---

## 7. MEMORY — 窗口框架包，长期记忆全自建 {#7-memory}

**类别**：`MEMORY`
**框架版位置**：[MemoryService.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/MemoryService.java) 类级 Javadoc，[TaskAgentService.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskAgentService.java) `memoryFor` 上方

### 手写版

`MemoryRetriever` 查库 + `buildContext` 拼 `[RETRIEVED MEMORY]` 块（见 §2），
历史每次查库全量拼、无窗口概念、超长自己截。

### 框架版（`langchain4j/stateful-agent`）

```java
ChatMemory memoryFor(Object memoryId) {
    return memories.computeIfAbsent(
            memoryId, id -> MessageWindowChatMemory.withMaxMessages(MAX_WINDOW_MESSAGES));
}
// 传 conversationId 当 memoryId，一个会话一个窗口，互不串话。
```

长期记忆：`MemoryService.retrieve` 查库 → `withMemories` 拼 `[长期记忆：…]` 前缀。

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| 短期历史 | 查库全量拼 | 每会话独立窗口，超 20 条自动丢旧 |
| 窗口寿命 | 随库永久 | 内存，重启即丢（message 表不受影响） |
| 长期记忆 | 自建 | 自建（框架无此概念） |
| 注入位置 | Context 对象 | 调代理前拼字符串；Spring AI 版是顾问链上的一环 |

### 取舍

- 得：窗口淘汰不用自己写截断。
- 失：窗口内容不可见，靠 `inspectWindow` 暴露；长期记忆注入逻辑裸露在调用处，
  比 Spring AI 的顾问少一层抽象、直白但啰嗦。
- 三者必须分开：窗口（给模型看）≠ 流水（给人看）≠ 偏好（跨会话）。

### 实际运行证据

`ConversationMemoryTest`：两会话窗口互不相同且 Pepper 含各自内容；
`longTermMemoryIsInjected`：窗口含 `[长期记忆…早上学习效率高]` 前缀。

---

## 8. HTTP + CONFIG — 客户端消失，装配还在 {#8-http}

**类别**：`HTTP` / `CONFIG`
**框架版位置**：[ChatModelFactory.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/config/ChatModelFactory.java) 类级 Javadoc，[StatefulAgentApp.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/StatefulAgentApp.java) `main` 装配块上方

### 手写版（stage02 `OpenAiCompatibleLlmClient`，约 120 行）

`HttpClient` 手建、鉴权头手拼、body 手序列化、响应手解析、重试超时自己写。

### 框架版（`langchain4j/stateful-agent`）

```java
return OpenAiChatModel.builder()
        .baseUrl(config.baseUrl())
        .apiKey(config.apiKey())
        .modelName(config.model())
        .timeout(config.timeout())
        .maxRetries(2)
        .build();
```

装配（`StatefulAgentApp.main`）仍一行行 `new`：框架没有接管装配（本模块没用注入框架），
对应的是手写版 `AppComponents.build`。

### 差异本质

| 维度 | 手写 | 框架 |
|---|---|---|
| HTTP | 120 行样板 | 一个 builder |
| 装配 | 手写工厂 | 手写 main（装配对象变了：Runner → 代理 + ChatModel） |
| 离线降级 | `FakeLlmClient` | `OfflineChatModel`（同样实现模型接口，行为可替换） |

### 取舍

- 得：HTTP 样板消失。
- 失：请求细节藏进框架，排错靠开日志而不是读代码。

---

## 9. WEB + TRACE — 调试台沿用手写版，工具账自己记 {#9-web}

**类别**：`CONFIG` / `TRACE`
**框架版位置**：[WebServer.java](../../langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/web/WebServer.java) 类级 Javadoc

### 手写版

`WebMain`：JDK `HttpServer`，左 Chat、右 State/Plan/Memory。

### 框架版（`langchain4j/stateful-agent`）

同样 JDK `HttpServer`，端点与 Spring AI 版对齐
（`POST /conversations`、`/chat`、`GET /runs/{id}`、`/messages`），
外加 `GET /debug/conversations/{id}`（窗口 / 记忆 / 工具调用 / 历史）。
静态页比 Spring AI 版多一块（Spring AI 版 static 留空，本模块带单页调试台）。

### 差异本质

| 维度 | 手写 | 本模块 | Spring AI 版 |
|---|---|---|---|
| Web 实现 | JDK HttpServer | 同样 JDK HttpServer | Spring Boot |
| 进度可见性 | `System.out` 打印 | 读自建表 + 工具记账 | 读自建表 |
| 工具调用可见性 | 打印 | `TaskTools.recentCalls`（方法内记账） | 无（藏在顾问里） |

### 取舍

- 换框架不用换 Web 层：证明 Web 与 Agent 逻辑解耦。
- 声明式代理的最大代价是"中间过程看不见"，本模块用两处手工记账补回：
  工具方法记调用、服务暴露窗口。这两处是声明式方案的标准配套动作。

---

## 本模块没有打标记的类

| 类 | 为什么不打 |
|---|---|
| `domain/` 下全部（Task、TaskService、各 Repository、Plan、AgentRun…） | 纯业务，删掉框架也要原样写一份，无可对比 |
| `config/ModelConfig` | 纯配置加载，框架不管 |
| `experiments/AgenticApiProbe` | 观察记录而非接管点；标记只打"框架替我做了事"的地方 |
| `support/FakeChatModel`（测试） | 测试桩，不是框架用法 |
| 测试类 | 测试本身不是对比点（比较的是被测行为，已在上面覆盖） |

## 本章刻意没做什么

- RAG：与 Spring AI Stateful 对标不需要，不加。
- Agentic 实验性 API：主路径不用，只留观察记录（beta 线，未引入）。
- 记忆提取（手写版 `MemoryExtractor`）：与 Spring AI 版同口径，只做检索注入。
- 检查点/回放、人工确认、MCP、多智能体：留给后续章节。
- 向量检索：长期记忆只做关键字 LIKE，与 Spring AI 版一致。

## 本章 `@FW-CMP` 标记清单

```bash
$ grep -rn "@FW-CMP" langchain4j/stateful-agent/src
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/StatefulAgentApp.java:25: * <p>@FW-CMP 本类对应手写版 {@code AppComponents.build}（手动 new 客户端、传仓库、组装 Runner）。
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/StatefulAgentApp.java:37:        // @FW-CMP [CONFIG] 装配块：不用框架就得自己写这一段
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/config/ChatModelFactory.java:12: * <p>@FW-CMP [HTTP] HTTP 客户端不用手写
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/config/ChatModelFactory.java:32:    // @FW-CMP [CONFIG] 装配从手写工厂方法搬到框架 builder
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/web/WebServer.java:30: * <p>@FW-CMP [CONFIG] Web 层框架不管，自己用 JDK 现成的
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/MemoryService.java:10: * <p>@FW-CMP [MEMORY] 长期记忆框架不管，只能自建
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/Planner.java:13: * <p>@FW-CMP [SCHEMA] 结构化输出靠提示词加手工解析
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskTools.java:16: * <p>@FW-CMP 本类对应手写版三个类的合集：
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskTools.java:40:    // @FW-CMP [SCHEMA] 工具 schema 的生成方式
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/PlanExecutor.java:15: * <p>@FW-CMP [TOOL_LOOP] 外层 Plan 循环框架没有概念，只能自建
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskAgentService.java:18: * <p>@FW-CMP [CONTEXT] 上下文从手写对象搬到框架代理
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskAgentService.java:51:        // @FW-CMP [TOOL_LOOP] 内层"模型→工具→模型"循环
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/TaskAgentService.java:75:    // @FW-CMP [MEMORY] 多会话记忆隔离：一个会话一个窗口
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/StatefulAssistant.java:10: * <p>@FW-CMP 本接口对应手写版 {@code StatefulAgentRunner.executeStep} 整套循环 +
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/StatefulAssistant.java:18:    // @FW-CMP [PROMPT] 系统提示词替代流程控制代码
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/agent/Replanner.java:9: * <p>@FW-CMP [PROMPT] 重规划没有专用 API，全靠提示词讲清楚
langchain4j/stateful-agent/src/main/java/com/example/langchain4j/stateful/domain/PlanRepository.java:18: * <p>@FW-CMP 本类对应手写版 {@code PlanRepository}（plan / plan_step 两张表 + 步骤推进）。
```
