package com.example.agentlearning.stage03;

/**
 * 步骤工具执行器：执行一个计划步骤对应的工具。
 * 教学版本只提供确定性 {@code echo} 工具（返回参数原样），保证每一步可复现。
 */
public final class StepExecutor {

    /** 执行 tool；若工具未知则返回错误说明。 */
    public String execute(String tool, String args) {
        if ("echo".equals(tool)) {
            return args == null ? "" : args;
        }
        return "未知工具 [" + tool + "]，无法执行";
    }
}