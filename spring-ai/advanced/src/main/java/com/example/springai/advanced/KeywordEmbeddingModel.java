package com.example.springai.advanced;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 离线可测的桩 Embedding 实现：关键词哈希向量。
 *
 * <p>真实 Embedding 模型需要联网调 API，单元测试不能依赖它。
 * 本类把文本切成词（英文按空白/标点分词，中文逐字），每个词哈希到固定维度，
 * 再做归一化。相同关键词越多的两段文本，向量余弦相似度越高，
 * 因此"查 BLOCKED 规范"能稳定命中 BLOCKED 段落，行为可预测、可断言。
 *
 * <p>这不是语义向量，只是教学用的相似度占位；生产环境换成真实模型即可，
 * 上层的 {@link PolicyKnowledgeBase} 不需要改。
 */
public class KeywordEmbeddingModel implements EmbeddingModel {

    /** 向量维度，教学够用即可。 */
    static final int DIMENSIONS = 64;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>(request.getInstructions().size());
        for (int i = 0; i < request.getInstructions().size(); i++) {
            embeddings.add(new Embedding(vectorize(request.getInstructions().get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorize(getEmbeddingContent(document));
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /**
     * 文本 → 归一化二值词袋向量：词出现记 1，不计次数。
     *
     * <p>刻意不用词频：知识库里"任务"这类通用词出现次数多，
     * 计次数会淹没"BLOCKED"这种真正能区分段落的关键词。
     * null/空文本返回零向量。
     */
    static float[] vectorize(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isBlank()) {
            return vector;
        }
        for (String token : tokenize(text)) {
            int index = Math.floorMod(token.hashCode(), DIMENSIONS);
            vector[index] = 1.0f;
        }
        float norm = 0.0f;
        for (float value : vector) {
            norm += value * value;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    /**
     * 分词：连续的 ASCII 字母数字算一个词（如 BLOCKED、T-2 中的 T 和 2），
     * 每个中日韩字符单独成词（如"阻塞"拆成"阻"+"塞"），其余字符作分隔符。
     */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isAsciiWordChar(c)) {
                word.append(Character.toLowerCase(c));
            } else {
                flush(word, tokens);
                if (isCjk(c)) {
                    tokens.add(String.valueOf(c));
                }
            }
        }
        flush(word, tokens);
        return tokens;
    }

    private static void flush(StringBuilder word, List<String> tokens) {
        if (!word.isEmpty()) {
            tokens.add(word.toString());
            word.setLength(0);
        }
    }

    private static boolean isAsciiWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

}
