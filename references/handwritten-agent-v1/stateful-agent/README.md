# stage02-stateful-agent

阶段整合二：**有状态 Agent**（Conversation / Agent State / Plan / Long-term Memory + Web 聊天调试台）。

对应阶段说明：`docs/stages/Stage2_StatefulAgent.md`
对应实现 Prompt：`docs/prompts/stage02_stateful_agent.md`

---

## 1. 本模块学什么

把前面 Lab 学过的几个机制，在一个独立 Stage 里**整合**成能持续处理会话和任务的 Agent，
并要求你从数据库层面看出它们各自的边界：

| 概念 | 对应数据库表 | 回答的问题 |
|---|---|---|
| **Conversation History** | `message` | 这个会话"聊过什么"（完整流水账） |
| **Agent State** | `agent_run` | 这个任务"干到哪了"（goal / status / step） |
| **Plan** | `plan` + `plan_step` | 目标"要怎么拆、每步做到哪" |
| **Long-term Memory** | `memory` | 用户"长期是什么样"（偏好/事实/约定） |

Web UI 把上面四类数据分别显示在右侧的 **State / Plan / Memory** 三个区域，
让学习者一眼看出：**它们是不同的数据，不能用一张 `messages` 列表代替。**

## 2. 为什么需要这四个概念（而不是只存 messages）

- **Conversation History**：重建每轮上下文，模型才能"记得刚说过什么"。
- **Agent State**：程序需要知道任务是否完成、跑到第几步，才能推进/停止/继续。
  只存对话消息，"是否完成"只能靠猜。
- **Plan**：长目标要先拆成可执行步骤，且每步状态（PENDING/RUNNING/DONE/FAILED）可观察、失败可重规划。
- **Long-term Memory**：跨会话仍值得保留的偏好、事实，是要"检索复用"的，
  和"聊过一次就翻篇"的对话消息语义完全不同。

它们没能混成一张 `messages` 的关键点：

- 一条偏好（Memory）**不属于任何一次会话**，而是属于"用户"；
- 一次执行（State / Plan）**不止一个用户说的话**，而是程序内部的控制信息；
- 只存 messages，就无法表达"这个任务跑到第几步、哪一步失败了、我有哪些长期偏好"。

## 3. 快速跑起来

### 3.1 CLI

在仓库根目录：

```bash
# 离线演示：Session A 保存 Maven 偏好 → 退出 → Session B 检索并执行
mvn -pl stages/stage02-stateful-agent compile exec:java \
    -Dexec.mainClass=com.example.agentlearning.stage02.Main \
    -Dexec.args="--demo"

# 交互模式：连接/新建会话、发消息、查看 state / plan / memory
mvn -pl stages/stage02-stateful-agent compile exec:java
```

有真实模型时（仓库根目录 `.env`），会自动用 `OpenAiCompatibleLlmClient`；
否则退化为 `FakeLlmClient` 剧本，演示同样可观察。

### 3.2 Web 调试台

```bash
mvn -pl stages/stage02-stateful-agent compile exec:java \
    -Dexec.mainClass=com.example.agentlearning.stage02.WebMain
# 然后浏览器打开 http://localhost:8080
```

端口可用第一个参数覆盖，例如 `WebMain 9000`。

## 4. Web 页面每个区域对应什么 Agent 概念

- **左侧中部（Conversation/…）**：会话列表 + 当前对话消息（`message` 表 = Conversation History）。
- **输入框 + 发送**：把消息交给后端 `AgentApplicationService`。
- **右上 Current Agent State**：最近一次 `AgentRun`（runId / goal / status / currentStep，来自 `agent_run` 表）。
- **右中 Plan**：最近一次 `Plan` 及其每步状态（来自 `plan` + `plan_step` 表）。
- **右下 Retrieved Memory**：本轮检索到的长期记忆（来自 `memory` 表）。

页面数据全部来自后端 SQLite 真实状态，不在浏览器里伪造第二套状态源。

### 建议观察的流程

1. 新建会话，输入「我的学习 Demo 都使用 Maven」。
   观察 Memory 区域出现"已保存 PREFERENCE Maven"。
2. 新建另一个会话，输入「我接下来的学习 Demo 都使用 Maven，帮我创建第一个学习项目」。
   观察 **Retrieved Memory** 出现了上一条 Maven 记忆（跨会话取回），
   同时 Plan 逐步 DONE、Agent 用 Maven 创建了任务。
3. 关闭进程再重启 Web，回到这个会话，消息仍然在（持久化）。

## 5. 代码里核心机制在哪

```
AgentApplicationService.chat(conversationId, userInput)
 ├─ 1) MessageRepository.append        → Conversation History 落库
 ├─ 2) MemoryExtractor + MemoryRepository.save → Long-term Memory
 ├─ 3) Planner.createPlan              → 生成 Plan
 ├─ 4) StatefulAgentRunner.run         → Agent Loop + Plan 逐步执行
 └─ 5) MemoryRetriever.retrieve        → 本轮检索记忆
```

`StatefulAgentRunner` 内部：每个计划步骤执行一个小的 ReAct 循环
（模型决策 → 工具 → Observation → 最终回答）；某步工具失败时调用 `Replanner` 重规划（有上限）。

Web 层（`WebMain` + handlers）只做 HTTP/JSON 转换，不包含任何 Agent 决策逻辑；
CLI 与 Web 复用同一个 `AppComponents`。

## 6. 测试

```bash
mvn -pl stages/stage02-stateful-agent test
```

`AgentApplicationServiceTest` 用 `FakeLlmClient` 驱动，验证：

1. 用户消息被持久化（Conversation History）；
2. 偏好被写入 `memory`，并在"重启后"的新会话被检索到（Long-term Memory）；
3. 计划被创建、步骤推进到 DONE（Plan）；
4. Agent State（goal / status / step）被持久化；
5. 工具失败触发 Replan；
6. 非偏好消息不保存为记忆。

测试不依赖真实在线模型。

## 7. 本 Stage 刻意没有实现什么

- **RAG**：记忆检索只用 keyword LIKE，没有向量检索；
- **Context Compaction / Checkpoint**：不压缩上下文、不保存单步断点（那是 Stage 3）；
- **Multi-Agent / Skill / MCP / Guardrail**：不在本阶段引入（Stage 4 再讲）；
- **前端工程化**：不用 Vue/React/npm，只有原生 HTML/CSS/JS + 简单轮询。