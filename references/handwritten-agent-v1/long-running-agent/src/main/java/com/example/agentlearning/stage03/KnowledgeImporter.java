package com.example.agentlearning.stage03;

import java.time.Instant;
import java.util.List;

/**
 * 知识文档导入器：向 {@code knowledge_doc} 表写入默认的项目规范文档。
 * <p>
 * 教学中读取内嵌文档，不依赖外部文件路径。
 */
public final class KnowledgeImporter {

    private final KnowledgeRepository repository;

    public KnowledgeImporter(KnowledgeRepository repository) {
        this.repository = repository;
    }

    /** 导入内嵌的三份规范文档。 */
    public void importDefaults() {
        if (repository.count() > 0) {
            return;
        }
        String now = Instant.now().toString();
        repository.insert(new KnowledgeDoc("d1", "数据模型规范",
                "所有实体使用 SQLite 存储，主键使用 TEXT UUID。"
                        + "字段命名使用下划线风格。必需记录 created_at。"
                        + "数字字段优先用 INTEGER，文本用 TEXT。"
                        + "关联关系用外键约束明确表达。",
                "database,sqlite,规范", now));
        repository.insert(new KnowledgeDoc("d2", "分层架构规范",
                "项目采用分层架构：Database（数据访问）、Repository（仓储）、"
                        + "Service（业务编排）、Web（HTTP 适配器）。"
                        + "上层依赖下层，下层不感知上层。"
                        + "Service 层包含核心 Agent 逻辑，Web 层只做 HTTP/JSON 转换。"
                        + "不允许在 Web Handler 中实现 Agent 决策。",
                "architecture,分层,规范", now));
        repository.insert(new KnowledgeDoc("d3", "代码质量规范",
                "所有 Java 类必须位于正确的包名和模块。"
                        + "测试不依赖真实 LLM，使用 ScriptedLlmClient 或 FakeLlmClient。"
                        + "核心循环和状态流转必须有单元测试覆盖。"
                        + "无用的 import、异常、字段不允许提交。"
                        + "System.out 仅在 Main 和 WebMain 中用于控制台输出。",
                "quality,测试,规范", now));
    }

    public void importDoc(KnowledgeDoc doc) {
        repository.insert(doc);
    }
}