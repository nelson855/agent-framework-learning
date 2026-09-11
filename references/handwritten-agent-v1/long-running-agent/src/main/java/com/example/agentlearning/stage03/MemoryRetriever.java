package com.example.agentlearning.stage03;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆检索器：用查询去 {@code memory} 表里找相关用户长期记忆。
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