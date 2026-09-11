package com.example.agentlearning.stage04;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地知识库（内存态）：固定文档供 Agent 通过 {@code getDoc(title)} 检索。
 *
 * <p>不持久化（教学级设计）。知识点包括：
 * <ul>
 *   <li>「高风险操作规范」：删除任务需要审批</li>
 *   <li>「任务管理规范」：任务创建规则</li>
 * </ul>
 */
public final class KnowledgeStore {

    private final Map<String, Doc> docs = new LinkedHashMap<>();

    /** 构建默认的演示知识。 */
    public KnowledgeStore() {
        put("高风险操作规范",
                "高风险操作规范\n" +
                        "1. 删除任务（deleteTask）属于高风险操作。\n" +
                        "2. 执行 deleteTask 前必须经过人工审批（Approval）。\n" +
                        "3. 审批通过后系统才真正执行删除。\n" +
                        "4. 未获得人工批准的删除请求将被拒绝。");
        put("任务管理规范",
                "任务管理规范\n" +
                        "1. 每个任务有 id、title、status（OPEN/IN_PROGRESS/DONE）。\n" +
                        "2. 创建任务时默认 status=OPEN。\n" +
                        "3. 任务可通过 createTask 工具创建，通过 deleteTask 删除（需审批）。\n" +
                        "4. 对知识不熟悉时先用 getDoc 查询。");
    }

    public KnowledgeStore put(String title, String content) {
        docs.put(title, new Doc(title, content));
        return this;
    }

    /** 按完全匹配标题查询。 */
    public Doc findByTitle(String title) {
        return docs.get(title);
    }

    /** 按关键词检索多个相关文档。 */
    public List<Doc> findByKeyword(String keyword) {
        String kw = keyword.toLowerCase();
        List<Doc> hits = new ArrayList<>();
        for (Doc d : docs.values()) {
            if (d.title().toLowerCase().contains(kw) || d.content().toLowerCase().contains(kw)) {
                hits.add(d);
            }
        }
        return hits;
    }

    public List<String> allTitles() {
        return List.copyOf(docs.keySet());
    }

    public record Doc(String title, String content) {
    }
}