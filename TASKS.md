# 学习进度清单（TASKS）

> 用途：记录整套教材 10 章的完成情况。每次开工前先读本文件感知进度，
> 每完成一章即时勾选对应项，不要等到最后统一改。
> 更新日期：2026-09-22

## 总览

- 已完成：Ch01 / Ch02 / Ch03 / Ch04（用户 2026-09-14 确认前三章；Ch04 2026-09-22 落盘）
- 下一章：Ch05 LangChain4j 重做带状态的助手
- 备注：Ch03 的 `spring-ai/stateful-agent/` 已落盘（Stage A/B/C，2026-09-16，22 测试全过），
  含对比文档 `docs/comparisons/ch03_spring_ai_stateful.md`。
  Ch04 的 `spring-ai/advanced/` 已落盘（RAG + MCP + Observability，2026-09-22，12 测试全过），
  含对比文档 `docs/comparisons/ch04_spring_ai_advanced.md`。

## 章节清单

- [x] Ch01 从手写 Agent 到框架
  - 教材：`docs/chapters/01_从手写Agent到Framework.md`
  - 步骤：`docs/prompts/01_analyze_handwritten_agent.md`
  - 产物：`docs/comparisons/handwritten_baseline_analysis.md`
- [x] Ch02 Spring AI 核心抽象
  - 教材：`docs/chapters/02_SpringAI核心抽象.md`
  - 步骤：`docs/prompts/02_spring_ai_basic.md`
  - 产物：`spring-ai/basic/`（含 README 的调用序列和框架对照表）
- [x] Ch03 用 Spring AI 重做带状态的助手
  - 教材：`docs/chapters/03_SpringAI重做StatefulAgent.md`
  - 步骤：`docs/prompts/03_spring_ai_stateful_agent.md`
  - 产物：`spring-ai/stateful-agent/`（Stage A/B/C 已落盘：35 类 + 22 测试 + 对比文档）
- [x] Ch04 Spring AI 进阶（RAG / MCP / 可观察性）
  - 教材：`docs/chapters/04_SpringAI进阶_RAG_MCP_Observability.md`
  - 步骤：`docs/prompts/04_spring_ai_advanced.md`
  - 产物：`spring-ai/advanced/`（已落盘：17 主类 + 4 测试类 12 测试 + 对比文档 `docs/comparisons/ch04_spring_ai_advanced.md`）
- [ ] Ch05 用 LangChain4j 重做带状态的助手
  - 教材：`docs/chapters/05_LangChain4j重做StatefulAgent.md`
  - 步骤：`docs/prompts/05_langchain4j_stateful_agent.md`
  - 产物：`langchain4j/stateful-agent/`
- [ ] Ch06 Spring AI 和 LangChain4j 对比
  - 教材：`docs/chapters/06_SpringAI_vs_LangChain4j.md`
  - 步骤：`docs/prompts/06_compare_java_frameworks.md`
  - 产物：`docs/comparisons/` 下的对比报告
- [ ] Ch07 LangGraph 长任务助手
  - 教材：`docs/chapters/07_LangGraph_LongRunningAgent.md`
  - 步骤：`docs/prompts/07_langgraph_long_running_agent.md`
  - 产物：`langgraph/long-running-agent/`
- [ ] Ch08 Google ADK 多智能体
  - 教材：`docs/chapters/08_GoogleADK_MultiAgent.md`
  - 步骤：`docs/prompts/08_google_adk_multi_agent.md`
  - 产物：`google-adk/multi-agent/`
- [ ] Ch09 智能体开发包与运行环境
  - 教材：`docs/chapters/09_AgentSDK与Harness.md`
  - 步骤：`docs/prompts/09_agent_sdk_harness.md`
  - 产物：`sdk-experiments/`
- [ ] Ch10 框架选型与最终综合
  - 教材：`docs/chapters/10_框架选型与最终综合.md`
  - 步骤：`docs/prompts/10_framework_selection_capstone.md`
  - 产物：`docs/comparisons/final_selection_matrix.md` 等总结文档

## 使用约定

1. 开工下一章前，先读本文件确定起点，再读对应教材和步骤文件。
2. 每完成一章，就把上面对应行的 `[ ]` 改成 `[x]`，并更新顶部的总览和日期。
3. 勾选以仓库里的真实产物为准（代码加文档都能找到），只看完文档不算完成。
4. 收尾前对照本清单逐项核对，清单和实际进度必须一致。
