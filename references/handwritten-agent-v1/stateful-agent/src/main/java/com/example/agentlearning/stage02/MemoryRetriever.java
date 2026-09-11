package com.example.agentlearning.stage02;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆检索器：用用户当前输入去 {@code memory} 表里找相关记忆。
 *
 * <p>不使用向量数据库，检索靠三件事：
 * <ol>
 *   <li>keyword：把输入切成词块，对 content 做 LIKE 匹配；</li>
 *   <li>type：可按类型过滤（{@code null} 表示不限）；</li>
 *   <li>排序：重要性（importance）优先、最近使用（last_used_at）其次，取前 N 条。</li>
 * </ol>
 * 检索命中会更新 {@code last_used_at}，让"常被用到的记忆"排得更前。
 */
public final class MemoryRetriever {

    private static final Pattern TOKEN = Pattern.compile("\\p{L}+\\p{N}*");

    private final MemoryRepository repository;

    public MemoryRetriever(MemoryRepository repository) {
        this.repository = repository;
    }

    public List<Memory> retrieve(String query, int limit) {
        List<String> keywords = tokenize(query);
        List<Memory> hits = repository.search(keywords, null, limit);
        for (Memory memory : hits) {
            repository.touch(memory.id());
        }
        return hits;
    }

    /** 把输入切成检索词：连续中英文/数字各算一个词块。 */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }
}