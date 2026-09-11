package com.example.agentlearning.stage03;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划器：为目标生成一份 6 步开发计划。
 *
 * <p>教学上采用确定性计划（不调用 LLM），每步指定工具与参数。
 * 每步的参数是"该步产出"的说明文字，用于演示上下文随步骤累积。
 */
public final class Planner {

    public List<PlanStep> createPlan(String goal) {
        List<PlanStep> steps = new ArrayList<>();
        steps.add(PlanStep.pending("S1", "收集项目规范需求", "echo",
                "收集并整理三份规范：数据模型规范、分层架构规范、代码质量规范，明确约束要点。"));
        steps.add(PlanStep.pending("S2", "设计数据模型", "echo",
                "按数据模型规范设计实体：主键 TEXT UUID、下划线命名、必含 created_at。"));
        steps.add(PlanStep.pending("S3", "设计分层架构", "echo",
                "按分层规范划分为 Database / Repository / Service / Web，Service 承载核心 Agent 逻辑。"));
        steps.add(PlanStep.pending("S4", "确定检索与上下文策略", "echo",
                "采用关键字检索 + SQLite LIKE；Context 区分 RAG、记忆、选中上下文、压缩摘要。"));
        steps.add(PlanStep.pending("S5", "确定压缩与检查点策略", "echo",
                "上下文超阈值压缩为结构化摘要；每步保存版本化 Checkpoint 支持中断恢复。"));
        steps.add(PlanStep.pending("S6", "生成符合规范的 JSON 总结", "echo",
                "汇总 6 步结果，生成符合代码质量规范的 JSON 交付总结并校验评估。"));
        return List.copyOf(steps);
    }
}