package com.example.agentlearning.stage04;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作记忆（内存态）：Agent 通过 {@code remember(key, value)} 写入、检索。
 *
 * <p>不持久化到 SQLite（教学级设计；Harness 不追求重启保留记忆）。
 * MEMORY_RETRIEVED 事件已记录到 Trace，用于 Web 面板展示。
 */
public final class MemoryStore {

    private final List<Entry> entries = new ArrayList<>();

    /** 写入一条记忆。返回记忆的摘要（用于 tool result）。 */
    public String remember(String key, String value) {
        entries.add(new Entry(key, value));
        return "已记住: " + key + " = " + value;
    }

    /** 按关键词检索记忆。 */
    public List<String> retrieve(String goal) {
        List<String> hits = new ArrayList<>();
        List<String> tokens = tokenize(goal);
        for (Entry e : entries) {
            if (matches(tokens, e)) {
                hits.add(e.key + " → " + e.value);
            }
        }
        return hits;
    }

    /** 中文没有空格分词，按标点/空格切出短语后与 key/value 双向子串匹配。 */
    private static boolean matches(List<String> tokens, Entry e) {
        for (String t : tokens) {
            if (t.length() >= 2
                    && (t.contains(e.key) || t.contains(e.value) || e.key.contains(t) || e.value.contains(t))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> tokenize(String goal) {
        List<String> out = new ArrayList<>();
        for (String part : goal.split("[，。！？、；：\\s]+")) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    public record Entry(String key, String value) {
    }
}