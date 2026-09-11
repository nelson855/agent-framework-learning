# Framework Mapping 规范

## 1. 为什么每章都必须做 Mapping

第二阶段最容易出现的假学习是：

> Codex 把项目写出来了，页面也能聊，但你只学会了复制框架示例。

因此每个框架项目 README 必须包含 Mapping。

## 2. 标准表格

```markdown
## Framework Mapping

| 能力 | 手写 Baseline | 当前框架 | 框架替代程度 | 仍由我们负责 |
|---|---|---|---|---|
| 模型调用 | LlmClient | ... | 高/中/低 | ... |
| Tool 定义 | Tool interface | ... | ... | ... |
| Tool Loop | AgentRunner while | ... | ... | ... |
| Conversation | ... | ... | ... | ... |
| Memory | ... | ... | ... | ... |
| State | ... | ... | ... | ... |
| Trace | ... | ... | ... | ... |
```

## 3. 必答问题

### A. 框架真正删除了哪些代码？

必须指出具体类/方法，而不是写“简化开发”。

### B. 哪些复杂度只是被搬走？

例如 Tool Loop 被框架接管，并不意味着：

- Tool 描述不需要设计；
- 权限不需要控制；
- 失败语义不需要定义。

### C. 控制权去哪了？

例如：

- 能否控制每一步 loop？
- 能否修改 tool result？
- 能否插入人工审批？
- 能否查看 intermediate messages？

### D. 框架边界在哪里？

哪些依然属于：

- Domain；
- Repository；
- Application Service；
- UI；
- Security。

## 4. 代码量不是唯一指标

不要只比较 LOC。

还要比较：

- 心智负担；
- 可观测性；
- 控制粒度；
- 测试难度；
- 框架锁定；
- 生态；
- 长任务能力；
- Multi-Agent 能力。
