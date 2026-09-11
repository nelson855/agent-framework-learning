# stage03-long-running-agent

**阶段整合三：Long-running Agent 调试台** —— 让 Agent 能执行更长时间的任务，并且不会因为上下文膨胀、程序中断、输出错误而轻易失败。

## 1. 本模块学什么

把 lab07~lab10 的机制整合成一个"能持续执行并恢复"的 Long-running Agent：

- **RAG 检索**：从本地规范文档查找知识（`knowledge_doc`）
- **记忆检索**：命中的用户长期记忆（`memory`）
- **Context Selection**：本次要喂给模型（或观察）哪一部分上下文
- **Compaction**：上下文过大时压缩为结构化摘要
- **Checkpoint（版本化）**：每执行一步保存一个新版本，不覆盖历史
- **Resume**：从最新 Checkpoint 继续，跳过已完成步骤
- **受控中断**：模拟任务执行到一半被打断，再恢复
- **Validator**：确定性校验最终交付 JSON（不调用模型）
- **Evaluator**：语义评估，反馈驱动有限重试

## 2. 为什么需要这些概念

一个简单的"调用一次 LLM 就返回"的 Agent 无法处理长任务：

| 长任务会遇到的坑 | 对应的机制 |
|---|---|
| 步骤结果越攒越多，上下文塞不下 | Context Selection + Compaction |
| 执行到一半 JVM/进程中断，全部白做 | 版本化 Checkpoint + Resume |
| 模型最后一刻输出一堆非法 JSON | 确定性 Validator 拦下来 |
| 模型输出"看起来对但内容不行" | Evaluator 语义把关 + 反馈重试 |

## 3. 项目结构

```text
src/main/java/com/example/agentlearning/stage03/
├── Main.java                 # CLI Demo（确定性脚本，不依赖真实 LLM）
├── WebMain.java              # Web 调试台入口（JDK HttpServer）
├── RunService.java           # 核心编排器：Web 与 CLI 共用
├── LongRunningRunner.java    # 每步执行 + Checkpoint 落点 + 中断判定
├── Planner.java              # 生成 6 步开发计划（确定性）
├── ContextBuilder.java       # 组装上下文 + 记录 ContextSnapshot
├── Compactor.java            # 超阈值压缩
├── CompactionSummarizer.java # 用 LLM 把结果压缩成结构化摘要
├── CheckpointRepository.java # 版本化 Checkpoint 存取
├── ProgramValidator.java     # 确定性校验最终 JSON
├── LlmEvaluator.java         # 语义评估
├── ...                        # 其余仓储 / 模型 / 工具
src/main/resources/web/       # index.html / app.js / styles.css
src/test/java/...             # 无需真实 LLM 的确定性测试
```

Web 层只做 HTTP/JSON 转换，核心逻辑全部在 `RunService`。删掉 Web 层，CLI 与测试照常运行。

## 4. 如何运行

### 4.1 测试

```bash
mvn -pl stages/stage03-long-running-agent test
```

> 若在受限沙箱环境遇到 SQLite 原生库加载失败，可加：
> `JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=\$TMPDIR"`

### 4.2 CLI Demo（推荐先跑这个看闭环）

```bash
mvn -pl stages/stage03-long-running-agent exec:java \
  -Dexec.mainClass=com.example.agentlearning.stage03.Main \
  -Dexec.args="data/stage03-demo.db"
```

演示序列：创建 run → 执行 S1/S2 → 请求在 S3 前中断 → Resume 从 Checkpoint 跳过 S1/S2 继续 → 完成 S6 → 生成 JSON → Validator 拒绝(首条非法) → 重试 → Evaluator 通过。

### 4.3 Web 调试台

```bash
mvn -pl stages/stage03-long-running-agent exec:java \
  -Dexec.mainClass=com.example.agentlearning.stage03.WebMain

# 打开 http://localhost:8080
```

（端口冲突可传第一个参数覆盖，例如 `-Dexec.args="8090"`）

接入真实 LLM：在仓库根目录放 `.env`（`LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL`）。未配置时自动使用离线脚本模型跑通全流程。

## 5. 运行时应该观察什么

在 CLI 日志或 Web 页面里明确观察：

| 概念 | 观察点 |
|---|---|
| RAG | Context 中有 `RETRIEVED KNOWLEDGE` 命中规范文档 |
| Memory | Context 中有 `RETRIEVED MEMORY` 命中用户约定 |
| Context Selection | 每步记录一个 ContextSnapshot，区分四块来源 |
| Compaction | 第 3 步起出现 `[COMPACT] stepResults N 条 → Summary v1` |
| Checkpoint | 每步 `CHECKPOINT SAVED version=N`，时间线递增不覆盖 |
| Interrupt | `RUN INTERRUPTED at step 3`，run=INTERRUPTED |
| Resume | 恢复后接着 S3 执行，S1/S2 不重做，最终 6 步全 DONE |
| Validator | `VALIDATOR REJECTED -> JSON 无法解析`（演示制造失败） |
| Evaluator | 先 `EVALUATOR REJECTED score=2`，反馈后 `EVALUATOR PASSED score=4` |

## 6. Web 页面对应关系

- **任务与动作**：Start / Step / Simulate Interruption / Resume / Evaluate
- **Run Overview**：runId、status、currentStep、是否已压缩
- **Plan**：6 步的状态（DONE / RUNNING / PENDING）与结果
- **Checkpoint Timeline**：version、savedAt、nextStep、COMPACTED 标记
- **Context Inspector**：RAG docs、记忆、压缩摘要、选中上下文快照（分区展示，非一个大字符串）
- **Validation / Evaluator**：每轮迭代的 validator / evaluator 通过与否与分数

## 7. 刻意没有实现什么

- **Multi-Agent**：没有 Orchestrator / Worker / Handoff（那是 stage04）
- **真实向量库**：教学用关键词 LIKE 检索，不做 Embedding
- **后台异步调度**：采用"逐步执行"的显式步骤模型，方便教学观察每一步
- **隐藏思维链**：不要求模型输出 chain-of-thought；只展示可观察的决策结果
- **生产级容错**：中断是受控的教学机制，不掩盖运行日志