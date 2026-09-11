# LOCAL_PATHS.example.md

> 复制本文件为 `LOCAL_PATHS.md`，只在你的本机使用。`LOCAL_PATHS.md` 已被 `.gitignore` 忽略。

第一阶段 Agent 学习仓库：

```text
HANDWRITTEN_AGENT_REPO=../agent-learning
```

如果你的旧仓库不在相邻目录，请改成实际路径，例如：

```text
HANDWRITTEN_AGENT_REPO=/your/local/path/agent-learning
```

只有 `docs/prompts/00_repository_bootstrap.md` 应读取这个路径。

Baseline 导入完成后，后续所有 Prompt 只能使用当前仓库内部的：

```text
references/handwritten-agent-v1/
```

不得继续依赖外部绝对路径。
