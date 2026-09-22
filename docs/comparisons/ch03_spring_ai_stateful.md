# Ch03 Stateful Agent — 手写 vs 框架 完整对比

> 本文件是 **第 3 章** 的完整对比文档，对应代码模块 `spring-ai/stateful-agent`。
> 代码里所有差异点都打了 `@FW-CMP` 标记；每个标记末尾有 `#x-xxx` 字符串指向本文档对应章节（全文搜索即可定位）。
>
> 全局搜索：
>
> ```bash
> grep -rn "@FW-CMP" spring-ai/stateful-agent/src
> ```
>
> 按类别过滤：
>
> ```bash
> grep -rn "@FW-CMP \[TOOL_LOOP\]" spring-ai/stateful-agent/src
> ```

## 本章节差异点导航

| # | 类别 | 位置（框架版） | 一句话差异 |
|---|---|---|---|
| 1 | `PROMPT` | [TaskAgentService.SYSTEM_PROMPT](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskAgentService.java) | 提示词只讲"角色"，不再背"工具说明书 + JSON 协议" |
| 2 | `CONTEXT` | [TaskAgentService（类级）](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskAgentService.java) | 上下文从"自己抱着的对象"变成"顾问链依次加工" |
| 3 | `TOOL_LOOP` 内层 | [TaskAgentService.chat()](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskAgentService.java) | 单步内"模型→工具→模型"：手写 for 循环 → 一次 `.call()` |
| 4 | `TOOL_LOOP` 外层 + `STATE` | [PlanExecutor（类级）](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/PlanExecutor.java) / [PlanRepository（类级）](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/domain/PlanRepository.java) | 外层计划推进框架没有概念，自建；状态表原样自建 |
| 5 | `SCHEMA` 工具 | [TaskTools.createTask](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskTools.java) | 工具说明书：手写 ToolDefinition → 注解反射 |
| 6 | `SCHEMA` 计划 | [Planner](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/Planner.java) / [Replanner](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/Replanner.java) | 计划 JSON 解析：手写 PlanParser → BeanOutputConverter |
| 7 | `MEMORY` | [MemoryService（类级）](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/MemoryService.java) / [MemoryInjectionAdvisor（类级）](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/MemoryInjectionAdvisor.java) | 长期记忆连存带取带注入全是业务自己的 |
| 8 | `HTTP` | [ChatModelConfig.chatModel](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/config/ChatModelConfig.java) | 不再手写 HTTP（与 Ch02 同理，本章只收录结论） |
| 9 | `TRACE` | [StateController（类级）](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/web/StateController.java) | 进度可观测性：手写打印 → 读自建表 |

---

## 1. PROMPT — 提示词只讲"角色"，不再背"说明书" {#1-prompt}

**类别**：`PROMPT`
**框架版位置**：[TaskAgentService.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskAgentService.java) `SYSTEM_PROMPT` 字段上方

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

### 框架版（`spring-ai/stateful-agent`）

```java
// spring-ai/stateful-agent/.../agent/TaskAgentService.java
private static final String SYSTEM_PROMPT = """
        你是任务管理助手。用户想创建任务就调用 createTask，
        想查询任务就调用 getTask，想更新状态就调用 updateTaskStatus。
        回答用中文，简短直接。
        """;
```

### 差异本质

| 维度 | 手写版 | 框架版 |
|---|---|---|
| 提示词内容 | 角色 + 工具 JSON 说明书 + 输出格式协议 | 只有角色和任务约定 |
| 工具说明谁给 | 自己拼进提示词（`toolsInstruction()`） | 框架反射 `@Tool` 注解自动随请求发 |
| 输出格式谁保证 | 提示词里约法三章 + 解析器兜底 | 模型返回标准 tool_calls，原生可读 |

**取舍**：提示词变短变好维护了；代价是"模型选错工具"时，调优位置从代码变成了注解上的名字和描述——`@Tool(description=...)` 写得好不好直接决定命中率。

---

## 2. CONTEXT — 上下文从"对象"变成"链" {#2-context}

**类别**：`CONTEXT`
**框架版位置**：[TaskAgentService.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskAgentService.java) 类级注释

### 手写版（stage02 `AgentContext` 完整类 + `buildContext` 完整方法）

```java
// references/handwritten-agent-v1/stateful-agent/.../stage02/AgentContext.java
public final class AgentContext {

    private final List<Message> messages = new ArrayList<>();

    public void addSystem(String content) {
        messages.add(Message.system(content));
    }

    public void addUser(String content) {
        messages.add(Message.user(content));
    }

    public void addAssistant(String modelReply) {
        messages.add(Message.assistant(modelReply));
    }

    /** 追加一次工具执行后的观察（带 [observation] 前缀）。 */
    public void addObservation(ToolCall call, ToolResult result, boolean success) {
        String text = "[observation] " + call.name() + "(" + call.arguments() + ") => "
                + (success ? "" : "[失败] ") + result.message();
        messages.add(Message.user(text));
    }

    public List<Message> messages() {
        return List.copyOf(messages);
    }
}
```

```java
// references/handwritten-agent-v1/stateful-agent/.../stage02/StatefulAgentRunner.java
private AgentContext buildContext(String conversationId, List<Memory> retrieved) {
    AgentContext context = new AgentContext();
    context.addSystem(systemPrompt());

    if (!retrieved.isEmpty()) {
        System.out.println("RETRIEVED MEMORY");
        StringBuilder block = new StringBuilder("[RETRIEVED MEMORY]\n");
        for (Memory memory : retrieved) {
            System.out.println("  - [" + memory.type() + "] " + memory.content());
            block.append("- [").append(memory.type()).append("] ").append(memory.content()).append('\n');
        }
        context.addSystem(block.toString().stripTrailing());
    }

    // 从库里重建已有对话历史（含刚追加的用户消息），保证多轮/重启后连续
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

**关键点**：上下文是一个自己抱着的 `AgentContext` 对象；每次调模型前，手动把系统提示、记忆块、历史消息拼好。

### 框架版（`spring-ai/stateful-agent`）

```java
// spring-ai/stateful-agent/.../agent/TaskAgentService.java（构造 + 调用）
this.chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();

String answer = chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user(userText)
        .advisors(spec -> spec
                .param(ChatMemory.CONVERSATION_ID, conversationId)
                .advisors(new MemoryInjectionAdvisor(memoryService, userId)))
        .tools(taskTools)
        .call()
        .content();
```

### 差异本质

| 维度 | 手写版 | 框架版 |
|---|---|---|
| 上下文载体 | 一个 `AgentContext` 对象，自己 add | 没有 Context 对象，一条顾问链依次加工 Prompt |
| 历史谁拼 | `buildContext` 从库读出来手动 add | `MessageChatMemoryAdvisor` 自动读写 `ChatMemory` |
| 记忆谁拼 | `buildContext` 里拼 `[RETRIEVED MEMORY]` 块 | `MemoryInjectionAdvisor` 包装成 `SystemMessage` 塞进 Prompt |
| 加新来源 | 改 `buildContext` 方法 | 加一个顾问，不碰编排代码 |

**取舍**：加新来源不用改主流程了；代价是上下文组装过程看不见——拼进去的东西对不对，只能靠日志和测试断言。

---

## 3. TOOL_LOOP（内层）— 单步循环收进一次调用 {#3-tool-loop-inner}

**类别**：`TOOL_LOOP`
**框架版位置**：[TaskAgentService.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskAgentService.java) `chat()` 方法上方

### 手写版（stage02 `StatefulAgentRunner.executeStep`，完整方法）

```java
// references/handwritten-agent-v1/stateful-agent/.../stage02/StatefulAgentRunner.java
/** 执行单个计划步骤：内部是一个小的 ReAct 循环，直到模型给出 final。 */
private StepOutcome executeStep(AgentContext context, AgentRun run, PlanStep step) {
    for (int i = 0; i < MAX_STEPS_PER_STEP; i++) {
        LlmResponse reply = llm.chat(context.messages());
        AgentDecision decision = AgentDecisionParser.parse(reply.content());
        System.out.println("MODEL_ACTION: " + decision.type());

        if (decision.isFinal()) {
            return StepOutcome.done(decision.answer());
        }

        ToolCall call = decision.toolCall();
        System.out.println("TOOL_CALL: " + call.name() + call.arguments());
        run = runs.updateStatus(run.runId(), RunStatus.WAITING_TOOL, planStepIndex(step));

        ToolResult result = tools.execute(call);
        context.addAssistant(reply.content());
        context.addObservation(call, result, result.success());
        System.out.println("TOOL_RESULT: " + result.message());

        if (!result.success()) {
            // 工具失败 → 这一步失败，需要 Replan
            return StepOutcome.failed(result.message());
        }
    }
    return StepOutcome.failed("单步 ReAct 循环超过最大步数 " + MAX_STEPS_PER_STEP);
}
```

**关键点**：约 30 行循环做五件事——调模型、解析决策、调工具、回填观察、记状态。`MAX_STEPS_PER_STEP = 6` 防单步死循环。

### 框架版（`spring-ai/stateful-agent`）

```java
.tools(taskTools)
.call()
```

### 差异本质

| 维度 | 手写版 | 框架版 |
|---|---|---|
| 循环位置 | `executeStep` 里的 for 循环，看得见 | `ToolCallingAdvisor` 里，看不见 |
| 决策解析 | `AgentDecisionParser` 从纯文本抠 JSON | 模型原生返回 tool_calls，无解析器 |
| 上限控制 | `MAX_STEPS_PER_STEP` 常量，改代码 | 框架默认配置，调参靠配置 |
| 失败语义 | `ToolResult.success()` 分支决定重规划 | 异常抛给外层，外层自己定 |

**取舍**：30 行循环消失；代价是循环细节（重试几次、超限怎么办）藏进框架，排障时要看框架日志而不是自己的代码。

---

## 4. TOOL_LOOP（外层）与 STATE — 计划推进框架没有概念 {#3-tool-loop-outer} {#4-plan-state}

**类别**：`TOOL_LOOP`（外层）+ `STATE`
**框架版位置**：[PlanExecutor.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/PlanExecutor.java) 类级注释 / [PlanRepository.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/domain/PlanRepository.java) 类级注释

> 本节是本章最重要的分界线：**内层循环框架管，外层自己写**。

### 手写版（stage02 `StatefulAgentRunner.run`，完整方法）

```java
// references/handwritten-agent-v1/stateful-agent/.../stage02/StatefulAgentRunner.java
public RunResult run(String conversationId, String userInput, Plan initialPlan) {
    String runId = "r-" + UUID.randomUUID().toString().substring(0, 8);
    AgentRun run = runs.create(runId, conversationId, userInput);
    plans.save(runId, initialPlan);

    // 开跑前检索长期记忆
    List<Memory> retrieved = retriever.retrieve(userInput, 3);
    AgentContext context = buildContext(conversationId, retrieved);

    System.out.println("RUN " + runId + " (goal=" + userInput + ")");

    Plan plan = initialPlan;
    int replans = 0;
    int idx = 0;
    while (idx < plan.steps().size()) {
        PlanStep step = plan.steps().get(idx);
        if (step.status() == PlanStepStatus.DONE || step.status() == PlanStepStatus.SKIPPED) {
            idx++;
            continue;
        }

        step.setStatus(PlanStepStatus.RUNNING);
        run = runs.updateStatus(runId, RunStatus.RUNNING, idx + 1);
        context.addSystem("当前执行计划步骤: [" + step.id() + "] " + step.description());
        System.out.println("EXEC STEP: [" + step.id() + "] " + step.description());

        StepOutcome outcome = executeStep(context, run, step);
        if (outcome.done()) {
            step.setStatus(PlanStepStatus.DONE);
            plans.updateStepStatuses(plan, runId);
            context.addAssistant(outcome.answer());
            System.out.println("STEP DONE: [" + step.id() + "] " + step.description());
            idx++;
            continue;
        }

        // 某步失败 → 请求 Replanner 重规划
        step.setStatus(PlanStepStatus.FAILED);
        step.setFailureReason(outcome.failureReason());
        plans.updateStepStatuses(plan, runId);
        System.out.println("STEP FAILED: [" + step.id() + "] " + step.description()
                + " reason=" + outcome.failureReason());

        if (replans >= MAX_REPLANS) {
            System.out.println("REPLAN LIMIT REACHED: maxReplans=" + MAX_REPLANS);
            for (PlanStep s : plan.steps()) {
                if (s.status() == PlanStepStatus.PENDING) {
                    s.setStatus(PlanStepStatus.SKIPPED);
                }
            }
            run = runs.updateStatus(runId, RunStatus.FAILED, idx + 1);
            String msg = "计划前置步骤失败且重规划次数已用尽，任务未完成: " + outcome.failureReason();
            messages.append(conversationId, "assistant", msg);
            return new RunResult(runId, msg, run);
        }

        replans++;
        plan = replanner.replan(plan, step);
        plans.save(runId, plan);
        System.out.println("REPLAN -> " + plan.steps().size() + " steps");
        idx = 0; // 从新计划的头部重新评估（已完成步骤会跳过）
    }

    run = runs.updateStatus(runId, RunStatus.COMPLETED, plan.steps().size());
    String answer = buildFinalAnswer(context);
    messages.append(conversationId, "assistant", answer);
    System.out.println("FINAL: " + answer);
    return new RunResult(runId, answer, run);
}
```

### 框架版（`spring-ai/stateful-agent`，`PlanExecutor.execute` 完整方法）

```java
// spring-ai/stateful-agent/.../agent/PlanExecutor.java
public AgentRun execute(String conversationId, String userId, String goal) {
    AgentRun run = runs.createRun(conversationId, goal);
    List<String> steps = planner.draftSteps(goal);
    Plan plan = plans.createPlan(run.runId(), steps);
    int replans = 0;
    int index = 0;
    while (index < plan.steps().size()) {
        PlanStep step = plan.steps().get(index);
        plans.updateStepStatus(plan.id(), step.id(), PlanStepStatus.RUNNING, null);
        try {
            agent.chat(conversationId, userId, step.description());
        } catch (RuntimeException e) {
            plans.updateStepStatus(plan.id(), step.id(), PlanStepStatus.FAILED, e.getMessage());
            if (replans >= MAX_REPLANS) {
                runs.updateStatus(run.runId(), RunStatus.FAILED, index);
                throw e;
            }
            replans++;
            List<String> fresh = replanner.replan(goal, step.description(), e.getMessage());
            plan = plans.createPlan(run.runId(), fresh);
            index = 0;
            continue;
        }
        plans.updateStepStatus(plan.id(), step.id(), PlanStepStatus.DONE, null);
        index++;
        runs.updateStatus(run.runId(), RunStatus.RUNNING, index);
    }
    runs.updateStatus(run.runId(), RunStatus.DONE, index);
    return runs.findById(run.runId()).orElseThrow();
}
```

### 差异本质

| 维度 | 手写版 | 框架版 |
|---|---|---|
| 外层循环 | `run()` 里的 while，自己写 | `execute()` 里的 while，同样自己写——框架不管 |
| 内层单步 | `executeStep()` 手写 ReAct 循环 | `agent.chat()` 一次框架调用 |
| 重规划产物 | 同一份计划原地改（`replan(plan, step)`），已完成步骤跳过 | 出一份新计划（`createPlan`），`findByRunId` 能查到全部历史 |
| 简化的语义 | `SKIPPED` / `WAITING_TOOL` / `COMPLETED` 更细 | 只有 PENDING/RUNNING/DONE/FAILED，RUNNING/DONE/FAILED |
| 状态落库 | `agent_run` + `plan` + `plan_step` 三张表 | 同样的三张表，结构几乎原样保留 |

**取舍**：外层循环两边都要手写——这是"框架在特定层接管"的铁证。本章故意保留了和手写版几乎一样的循环骨架，就是为了让读者看到：换框架省掉的是内层，不是外层。`STATE` 类别（`PlanRepository`、`AgentRunRepository`）同理：框架里没有"计划"这个概念，表结构和推进逻辑原样自建。

---

## 5. SCHEMA（工具）— 说明书不再手写 {#5-schema}

**类别**：`SCHEMA`
**框架版位置**：[TaskTools.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskTools.java) `createTask` 方法上方

### 手写版（stage02 `ToolRegistry` 完整类，核心两段）

```java
// references/handwritten-agent-v1/stateful-agent/.../stage02/ToolRegistry.java
/** 分派入口：查表 → 校验 → 执行。未知工具与参数错误都返回失败结果，不抛异常。 */
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

/** 把工具说明序列化成 JSON，拼进系统提示让模型知道有哪些工具可用。 */
public String toolsInstruction() {
    try {
        return objectMapper.writeValueAsString(definitions());
    } catch (JsonProcessingException e) {
        throw new IllegalStateException("序列化工具说明失败", e);
    }
}
```

配上每个工具手写的 `ToolDefinition`（名字、描述、参数表），三者（`Tool` + `ToolDefinition` + `ToolRegistry`）才凑齐"定义 + 说明 + 分派"。

### 框架版（`spring-ai/stateful-agent`）

```java
@Tool(name = "createTask", description = "用给定的标题创建一个新的学习任务。返回新任务的编号。")
public String createTask(
        @ToolParam(description = "要创建的任务标题，例如：学习 Spring AI Advisor") String title) {
    Task task = taskService.createTask(title);
    return task.id();
}
```

### 差异本质

| 维度 | 手写版 | 框架版 |
|---|---|---|
| 工具定义 | `ToolDefinition` 手写名字/描述/参数 | `@Tool` + `@ToolParam` 注解写在方法上 |
| 说明书生成 | `toolsInstruction()` 序列化后拼进提示词 | 框架反射签名自动生成，随请求发 |
| 分派执行 | `execute()` 查表 + 校验 + 执行 | 框架按 tool_calls 自动分派 |

**取舍**：三个类收成一个类；代价是参数校验从 `ArgumentValidator` 的显式逻辑变成框架按 schema 的隐式校验——非法参数的错误信息不如手写的可控。（与 Ch02 第 4 节同理，本章工具从 2 个变成 3 个，多了 `updateTaskStatus`。）

---

## 6. SCHEMA（计划）— 结构化输出替代手写解析 {#6-schema-plan}

**类别**：`SCHEMA`（Planner）+ `PROMPT`（Replanner）
**框架版位置**：[Planner.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/Planner.java) 类级注释 / [Replanner.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/Replanner.java) 类级注释

### 手写版（stage02 `Planner` + `PlanParser.parse`，完整方法）

```java
// references/handwritten-agent-v1/stateful-agent/.../stage02/Planner.java
public Plan createPlan(String goal) {
    LlmResponse reply = llm.chat(List.of(Message.system(PROMPT), Message.user(goal)));
    return PlanParser.parse(reply.content());
}
```

```java
// references/handwritten-agent-v1/stateful-agent/.../stage02/PlanParser.java
public static Plan parse(String json) {
    try {
        JsonNode root = MAPPER.readTree(json);
        String goal = root.path("goal").asText("未命名目标");
        JsonNode stepsNode = root.path("steps");
        if (!stepsNode.isArray()) {
            throw new IllegalArgumentException("计划缺少 steps 数组: " + json);
        }
        List<PlanStep> steps = new ArrayList<>();
        for (JsonNode node : stepsNode) {
            String id = node.path("id").asText("");
            String description = node.path("description").asText("");
            if (id.isBlank() || description.isBlank()) {
                throw new IllegalArgumentException("步骤缺少 id 或 description: " + node);
            }
            steps.add(new PlanStep(id, description));
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("计划步骤不能为空: " + json);
        }
        return new Plan(goal, steps);
    } catch (IllegalArgumentException e) {
        throw e;
    } catch (Exception e) {
        throw new IllegalArgumentException("无法解析计划 JSON: " + json, e);
    }
}
```

**关键点**：模型输出纯文本，自己用 Jackson 抠；格式一歪就崩，所以校验写得非常 defensive（缺数组、缺字段、空步骤三种错分别处理）。

### 框架版（`spring-ai/stateful-agent`）

```java
// spring-ai/stateful-agent/.../agent/Planner.java
public record StepList(List<String> steps) {
}

public List<String> draftSteps(String goal) {
    String content = chatClient.prompt()
            .system("把用户的目标拆成具体的执行步骤，只输出步骤清单。"
                    + converter.getFormat())
            .user(goal)
            .call()
            .content();
    StepList draft = converter.convert(content);
    return draft == null || draft.steps() == null ? List.of() : draft.steps();
}
```

### 差异本质

| 维度 | 手写版 | 框架版 |
|---|---|---|
| 格式约束 | 事后解析碰运气（`PlanParser` defensive 校验） | 事前 `getFormat()` 告诉模型照着填 |
| 步骤 id | 模型生成（S1/S2…），解析器校验 | 框架版不需要 id，`PlanRepository` 落库时自己生成 |
| 重规划输入 | `buildContext` 把整份计划状态拼成文本 | 只传目标 + 失败步骤 + 失败原因三句话 |

**取舍**：解析器的 defensive 代码消失；代价是步骤结构得先定义成 Java 类型（`StepList`），改结构要改类。`Replanner` 同理——连"重规划"这个动作框架都没有专用类，它只管单次结构化输出，什么算失败、失败几次算完，全是业务代码。

---

## 7. MEMORY — 长期记忆连存带取带注入全自建 {#7-memory}

**类别**：`MEMORY`
**框架版位置**：[MemoryService.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/MemoryService.java) 类级注释 / [MemoryInjectionAdvisor.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/MemoryInjectionAdvisor.java) 类级注释

### 手写版（stage02 `MemoryRetriever.retrieve` + 注入点，完整方法）

```java
// references/handwritten-agent-v1/stateful-agent/.../stage02/MemoryRetriever.java
public List<Memory> retrieve(String query, int limit) {
    List<String> keywords = tokenize(query);
    List<Memory> hits = repository.search(keywords, null, limit);
    for (Memory memory : hits) {
        repository.touch(memory.id());
    }
    return hits;
}
```

查到之后，在 `StatefulAgentRunner.buildContext` 里拼成 `[RETRIEVED MEMORY]` 块直接加进 `AgentContext`（见第 2 节）。

### 框架版（`spring-ai/stateful-agent`，检索 + 注入各一段）

```java
// spring-ai/stateful-agent/.../agent/MemoryService.java
public List<Memory> retrieve(String userId, String query, int limit) {
    if (userId == null || userId.isBlank() || query == null || query.isBlank()) {
        return List.of();
    }
    return repository.findByKeyword(userId.strip(), query.strip(), limit);
}
```

```java
// spring-ai/stateful-agent/.../agent/MemoryInjectionAdvisor.java
public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    String query = request.prompt().getUserMessage().getText();
    List<Memory> hits = memoryService.retrieve(userId, query, 5);
    if (hits.isEmpty()) {
        return chain.nextCall(request);
    }
    String extra = hits.stream()
            .map(m -> m.memoryKey() + "：" + m.memoryValue())
            .collect(Collectors.joining("\n"));
    List<Message> instructions = new ArrayList<>(request.prompt().getInstructions());
    instructions.add(0, new SystemMessage("用户长期记忆（仅供参考）：\n" + extra));
    Prompt augmented = new Prompt(instructions, request.prompt().getOptions());
    return chain.nextCall(request.mutate().prompt(augmented).build());
}
```

### 差异本质

| 维度 | 手写版 | 框架版 |
|---|---|---|
| 检索 | 分词 + LIKE + 按重要性排序 + 更新 `last_used_at` | 整句 LIKE，不分词、不排序 |
| 注入点 | 直接改自己的 `AgentContext` 对象 | 实现 `CallAdvisor` 接口，在链条里换一份新 Prompt |
| 框架管的部分 | 无 | 只管"传话"（顾问链），不管"记事"（查库） |

**取舍**：本章故意把检索做简化（对齐"关键字即可"的教学目标）；注入从"改对象"变成"换 Prompt"，第一次写会觉得绕，但加第二个注入源时不用改主流程。

### 附：三份"记忆"的区别（本章第二个重要教学点）

| 维度 | `message` 表（UI History） | `ChatMemory`（模型上下文） | `memory` 表（长期记忆） |
|---|---|---|---|
| 存哪 | SQLite，重启不丢 | 内存，重启丢 | SQLite，重启不丢 |
| 内容 | 完整流水账 | 窗口内最近 N 条 | 跨会话偏好（键值） |
| 谁负责 | `MessageRepository`（业务） | 框架（`MessageWindowChatMemory`） | `MemoryService` + 顾问（业务） |
| 对应物 | 手写版 `message` 表 | 手写版没有——原来历史全在库里 | 手写版 `memory` 表 |

---

## 8. HTTP — 不再手写请求 {#8-http}

**类别**：`HTTP`
**框架版位置**：[ChatModelConfig.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/config/ChatModelConfig.java) `chatModel` 方法上方

本节结论与 Ch02 第 3 节一致：手写版用 `HttpClient` 手拼 `/chat/completions` 请求、手动解析 `/choices/0/message/content`；框架版用 `OpenAiChatModel.builder()` 把地址、Key、超时配进去，HTTP 细节和序列化全收进框架。Spring AI 2.0 要求同步 + 异步两个客户端都给。本章不再展开，完整代码对比见 Ch02 文档。

本章新增的一个约束：Spring AI 2.0 的 JSON 解析模块依赖 Spring Framework 7 的类，所以模块的 Boot parent 用的是 **4.0.0**（3.5.16 带的 Framework 6.2 会报 `NoClassDefFoundError: Nullness`）。详见模块 README 的 Version Notes。

---

## 9. TRACE — 进度可观测性查自建表 {#9-trace}

**类别**：`TRACE`
**框架版位置**：[StateController.java](../../spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/web/StateController.java) 类级注释

### 手写版

stage02 靠满屏的 `System.out.println`（`RUN` / `EXEC STEP` / `MODEL_ACTION` / `TOOL_CALL` / `TOOL_RESULT` / `STEP DONE` / `REPLAN` / `FINAL`）看进度；落库的只有三张表。想在界面上显示"第 2/3 步"，得自己查表拼。

### 框架版（`spring-ai/stateful-agent`）

```java
// spring-ai/stateful-agent/.../web/StateController.java
@GetMapping("/{runId}")
public RunStateDto getState(@PathVariable("runId") String runId) {
    AgentRun run = runs.findById(runId).orElseThrow();
    var planDtos = plans.findByRunId(runId).stream()
            .map(p -> new RunStateDto.PlanDto(
                    p.id(),
                    p.steps().stream()
                            .map(s -> new RunStateDto.StepDto(
                                    s.id(), s.description(), s.status().name()))
                            .toList()))
            .toList();
    return new RunStateDto(
            run.runId(), run.goal(), run.status().name(), run.currentStep(), planDtos);
}
```

### 差异本质

| 维度 | 手写版 | 框架版 |
|---|---|---|
| 看进度 | 控制台打印 + 自己查三张表 | `GET /runs/{runId}` 接口，读同样的三张表 |
| 框架观测 | 无 | Spring AI 的 Micrometer 指标只管 token 级，不管业务进度 |

**取舍**：业务进度的可观测性框架不包——"第几步、成没成"这种问题，表和接口都得自己建。本章没接 Micrometer，要看 token 消耗得另起一轮。

---

## 本模块没有打标记的类

这些类是纯业务或纯接线，框架对它们一无所知，故意不打标记：

| 类 | 为什么没标记 |
|---|---|
| `domain/Task`、`TaskStatus` | 纯值对象 |
| `domain/TaskRepository`、`TaskService` | 任务业务，与 Ch02 同构，原样保留 |
| `domain/Conversation`、`Message`、`MessageRepository` | UI History 落库，框架不管 |
| `domain/Memory`、`MemoryRepository` | 长期记忆落库，框架不管 |
| `domain/Plan`、`PlanStep`、`PlanStepStatus` | 计划值对象 |
| `domain/AgentRun`、`RunStatus`、`AgentRunRepository` | 运行状态落库，框架不管 |
| `web/dto/*` | 接口形状定义 |
| `web/ConversationController`、`ChatController` | 薄控制器，只做路由和委托 |
| `config/ModelConfig` | 读 `.env`，与 Ch02 同构 |
| `StatefulAgentApplication` | 启动入口 |

---

## 本章 `@FW-CMP` 完整清单

```bash
$ grep -rn "@FW-CMP" spring-ai/stateful-agent/src
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/config/ChatModelConfig.java:35:    // @FW-CMP [HTTP] HTTP 客户端与请求/响应序列化
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskTools.java:12: * <p>@FW-CMP 本类对应手写版三个类的合集：
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskTools.java:28:    // @FW-CMP [SCHEMA] 工具 schema 的生成方式
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/Replanner.java:11: * <p>@FW-CMP [PROMPT] 重规划没有专用 API，全靠提示词讲清楚
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/MemoryInjectionAdvisor.java:19: * <p>@FW-CMP [MEMORY] 记忆注入只能手写，没有框架捷径
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/PlanExecutor.java:15: * <p>@FW-CMP [TOOL_LOOP] 外层 Plan 循环 Spring AI 不管，只能自建
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/MemoryService.java:10: * <p>@FW-CMP [MEMORY] 长期记忆框架不管，只能自建
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/Planner.java:11: * <p>@FW-CMP [SCHEMA] 结构化输出替代手写 JSON 解析
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/StateController.java:15: * <p>@FW-CMP [TRACE] 可观测性没有框架方案，查自建表
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskAgentService.java:11: * <p>@FW-CMP [CONTEXT] 上下文从手写对象搬到框架顾问链
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskAgentService.java:26:    // @FW-CMP [PROMPT] 系统提示词替代流程控制代码
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/agent/TaskAgentService.java:60:    // @FW-CMP [TOOL_LOOP] 内层"模型→工具→模型"循环
spring-ai/stateful-agent/src/main/java/com/example/springai/stateful/domain/PlanRepository.java:18: * <p>@FW-CMP [STATE] 计划与状态框架不管，只能自建
```

共 13 处（与 `grep` 输出逐行比对，一个不差）。

---

## 业务场景行为对照

### 场景 1：创建任务（"帮我建个'学游泳'的任务"）

| | 手写版 | 框架版 |
|---|---|---|
| 计划 | `Planner` 拆出 S1 建任务、S2 确认等多步 | `Planner.draftSteps` 拆出步骤 |
| 单步执行 | `executeStep` 循环：模型输出 JSON → 解析 → 调 `TaskTools` → 回填观察 | `agent.chat` 一次调用，框架内循环调 `createTask` |
| 落库 | `task` 表（`TaskStore`） | 同一张 `task` 表（`TaskRepository`） |
| 用户看到 | 最终总结里的一句话 | 某步的模型回复 + `message` 表流水 |

### 场景 2：多轮对话查任务（"刚才那个任务什么状态？"）

| | 手写版 | 框架版 |
|---|---|---|
| 历史来源 | `buildContext` 从 `message` 表重建全部历史 | `MessageChatMemoryAdvisor` 从 `ChatMemory` 取窗口内历史 |
| 长期记忆 | `retriever.retrieve` 拼 `[RETRIEVED MEMORY]` 块 | `MemoryInjectionAdvisor` 按用户输入关键字查 `memory` 表注入 |
| 重启后 | 历史全在库里，连续 | 窗口内历史丢失（内存），`message` 表流水还在 |

### 场景 3：更新任务状态（工具失败一次，比如任务不存在）

| | 手写版 | 框架版 |
|---|---|---|
| 失败信号 | `ToolResult.success() == false` | 工具抛异常 |
| 步骤标记 | `FAILED` + `failureReason` 写回 `plan_step` | 同样 `FAILED` + 异常信息写回 |
| 重规划 | `replan(plan, step)` 原地改同一份计划 | `replan(goal, step, reason)` 出一份新计划，旧计划保留可查 |
| 上限 | `MAX_REPLANS = 2`，用完标 `SKIPPED` + 运行 `FAILED` | 同样 2 次，用完运行记 `FAILED` 并抛错（无 `SKIPPED` 语义） |
