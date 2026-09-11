# 实现 Prompt 使用顺序

> 建议 Codex 每次只执行一个 Prompt。执行前先让它读取根目录 `AGENTS.md`。

| 顺序 | Prompt | 主要产物 |
|---:|---|---|
| 00 | `00_repository_bootstrap.md` | 导入 Handwritten Baseline，初始化目录 |
| 01 | `01_analyze_handwritten_agent.md` | 手写 Agent 架构基线报告 |
| 02 | `02_spring_ai_basic.md` | Spring AI Tool Loop 最小实验 |
| 03 | `03_spring_ai_stateful_agent.md` | Spring AI Benchmark A |
| 04 | `04_spring_ai_advanced.md` | Spring AI RAG/MCP/Observability |
| 05 | `05_langchain4j_stateful_agent.md` | LangChain4j Benchmark A |
| 06 | `06_compare_java_frameworks.md` | Java 框架对照报告 |
| 07 | `07_langgraph_long_running_agent.md` | LangGraph Benchmark B |
| 08 | `08_google_adk_multi_agent.md` | Google ADK Benchmark C |
| 09 | `09_agent_sdk_harness.md` | OpenAI/Claude SDK 小实验 |
| 10 | `10_framework_selection_capstone.md` | 最终选型报告 |

## 执行原则

每次执行完一个 Prompt 后，不要马上进入下一章。先：

1. 自己运行 Demo；
2. 阅读模块 README 的 `Framework Mapping`；
3. 对照本章思考题；
4. 把疑问写入学习记录；
5. 再进入下一章。
