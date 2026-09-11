package com.example.agentlearning.stage03;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检索用极简分词器。连续英文/数字为一个词块；汉字按二元组切分，
 * 使 LIKE 子串匹配能覆盖长中文问句中的关键词。
 */
public final class Tokens {

    private static final Pattern WORD = Pattern.compile("[a-zA-Z0-9]+");
    private static final Pattern CHINESE = Pattern.compile("[\\u4e00-\\u9fff]+");

    private Tokens() {
    }

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        Matcher word = WORD.matcher(text);
        while (word.find()) {
            tokens.add(word.group());
        }
        Matcher chinese = CHINESE.matcher(text);
        while (chinese.find()) {
            String block = chinese.group();
            if (block.length() == 1) {
                tokens.add(block);
            } else {
                for (int i = 0; i + 2 <= block.length(); i++) {
                    tokens.add(block.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }
}