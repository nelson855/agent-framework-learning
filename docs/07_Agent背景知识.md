# Agent 背景知识

> 用途：放各章都会用到的通用背景，不属于某一章专有的对比结论。
> 章节对比文档（`docs/comparisons/chXX_*.md`）只写"本章框架版 vs 手写版"；
> 这里写"这个概念本身是什么、和日常开发里熟悉的东西有什么区别"。
> 每节末尾标注"关联代码"和"关联章节"，方便来回跳转。

## 目录

- [1. MCP 和直接调 HTTP 接口的区别](#1-mcp-和直接调-http-接口的区别)
- [2. MCP 的三样本事（本章只用了 tools）](#2-mcp-的三样本事本章只用了-tools)

---

## 1. MCP 和直接调 HTTP 接口的区别

先说一句定心话：`spring-ai/advanced` 里的 `TaskMcpServer` 就是一个假扮的后端服务，
`TaskMcpClient` 就是调它的客户端。你的理解没错，这正是为了把"工具在另一个进程"摆到台面上。

### 1.1 一句话区别

直接调超文本传输协议（HyperText Transfer Protocol，HTTP）接口：调用方**事先就知道**
有哪些接口、每个接口要什么参数，这些知识写死在调用方代码里。

模型上下文协议（Model Context Protocol，MCP）：调用方**事先什么都不知道**，
先问一句"你有什么工具"（自报家门），拿到清单再调。后端加工具，调用方代码不用改。

### 1.2 功能上的区别：能不能"现问现有"

日常调 HTTP 接口的流程：后端定好地址（如 `GET /tasks/T-2`），调用方照文档拼参数发过去。
加一个新接口，调用方就要多写一段拼地址、拼参数、解析返回的代码。

MCP 的流程永远是两步：

1. 发 `tools/list` 问"我能调什么"——服务端把工具清单连同参数格式
   （JavaScript 对象简谱模式，JSON Schema，一种描述"参数长什么样"的格式）一起返回；
2. 发 `tools/call` 说"我要调这一个，参数是这些"——服务端执行并返回文本结果。

这一步"自报家门"恰恰是给模型看的：模型看不懂你的接口文档，
只能靠这份清单决定调哪个工具、填什么参数。
`AdvancedAgent` 里"发现几个工具就挂几个"的循环（`mcpClient.discover()`），
就是这套机制的直接体现——换成手写 HTTP 接口，每个地址都要手写一段挂载代码。

### 1.3 实现上的区别：协议和传输是两层皮

直接调 HTTP 时，地址、方法、参数、返回格式混在一起：`GET` 还是 `POST`、
路径怎么写、状态码算成功还是失败，每个接口自定规矩。

MCP 把这两层拆开：

- **协议层（说什么话）**：用远过程调用文本协议（JSON Remote Procedure Call，JSON-RPC，
  一种用文本传"调哪个方法、带什么参数"的规矩）信封装起来，
  永远是 `{"jsonrpc":"2.0","id":数字,"method":"tools/list 或 tools/call","params":{...}}`，
  回来也永远是 `result` 或 `error`。
  成功和失败的语义也统一了：业务失败（如任务不存在）装在 `result` 的文本里，
  让模型自己重试；只有管道断开、方法名不对、缺参数这类协议层面的事，
  才走 `error` 并在客户端抛异常中断。见 `TaskMcpClient.request()` 的判断逻辑。
- **传输层（话怎么送到）**：本章用的是标准输入输出（standard input/output，stdio）管道，
  不走网——`StdioProcessTransport.exchange()` 就是"往子进程写一行、读回一行"。
  同一套协议话术，生产环境可以换成走 HTTP 加服务器推送事件
  （Server-Sent Events，SSE，一种服务器向客户端推送消息的 HTTP 机制）的传输，
  业务代码不用动。`JsonRpcTransport` 这个接口就是为这层替换留的。

所以准确的说法是：MCP 客户端和手写 HTTP 客户端一样都是"发请求"，
但它发的请求格式统一、自带自报家门，且送信的路（管道还是网线）可以随时换。

### 1.4 为什么教学里两端都自己写

生产环境里服务端通常是别人提供的，你只需要客户端。
教学里两端都自己写，是为了让分界线看得见：
`TaskMcpServer` 那 100 行就是"搬到别人家去"的部分，
`TaskMcpClient` 加 `McpTaskToolCallback` 就是"为跨进程多付的代价"。
以后接真正的外部服务时，客户端这半边几乎原样照用，只换服务端地址。

### 关联

- 关联代码：`spring-ai/advanced/src/main/java/com/example/springai/advanced/TaskMcpServer.java`
  （`handleLine` 总入口、`toolsList` 自报家门、`toolsCall` 执行）、
  `TaskMcpClient.java`（`discover` / `call` / `request`）、
  `StdioProcessTransport.java`（`spawn` 建管道、`exchange` 收发）、
  `McpTaskToolCallback.java`（把发现的工具包装成框架能挂载的样子）。
- 关联章节：`docs/comparisons/ch04_spring_ai_advanced.md` 第 4、5 节。

---

## 2. MCP 的三样本事（本章只用了 tools）

先纠正一个容易产生的错觉：因为 `spring-ai/advanced` 里只演示了"发现工具并执行"，
会误以为 MCP 就等于远方工具。其实 MCP 规定的服务端本事有三类，本章只实现了第一类：

| # | 能力 | 一句话 | 本章 | 如果要接，在智能体里归宿在哪 |
|---|---|---|---|---|
| 1 | tools（工具） | 能动手干活：查状态、发邮件等。`tools/list` 自报家门，`tools/call` 执行 | 唯一的 `get_task_status` | 包成框架能挂载的样子，等模型点名执行 |
| 2 | resources（资源） | 能看不能动的资料：规范文档、配置文件等。读回来用，不"执行" | 未实现 | 取回来塞进上下文，走"递资料"的路，和检索查到的片段同类 |
| 3 | prompts（提示词模板） | 服务端预制的对话开场白或指令模板，取回填参数再发给模型 | 未实现 | 参与提示词组装，不进工具循环 |

所以"在智能体眼里所有 MCP 都是远方工具"只在本章的接法里成立——本章的包装只会包工具。
跳出本章看，MCP 是远方伸过来的三种手：一只手干活（工具），一只手递资料（资源），
一只手给话术（模板）。后两种走的是"进上下文"的路，而不是"被模型点名执行"的路。

### 关联

- 关联代码：`spring-ai/advanced/src/main/java/com/example/springai/advanced/TaskMcpServer.java`
  （只实现了 `tools/list` + `tools/call`，没有 resources/prompts 相关方法，可对照着看缺了哪两块）。
- 关联章节：`docs/comparisons/ch04_spring_ai_advanced.md` 第 4 节（工具那只手是怎么被包起来挂上框架的）。

---
