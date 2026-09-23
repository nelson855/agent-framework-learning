package com.example.springai.advanced;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;

/**
 * 任务规范知识库：负责文档 ingest 与向量检索（RAG 的前半段）。
 *
 * <p>@FW-CMP 本类对应手写版 {@code agent-harness/KnowledgeStore}（本地知识库）。
 * 手写版是内存 Map + 关键词匹配；本类把向量化、存储、相似度检索交给框架，
 * 只剩切段策略与 metadata 设计。
 *
 * <p>完整对比见 {@code docs/comparisons/ch04_spring_ai_advanced.md}。
 *
 * <p>启动时读 classpath 下的 {@code kb/task-policy.md}，按二级标题切段，
 * 每段变成一个 {@link Document}（metadata 带段标题与序号），写入 {@link SimpleVectorStore}。
 * 提问时按语义相似度取回最相关的段落，供 {@link RetrievalAdvisor} 注入模型上下文。
 */
public class PolicyKnowledgeBase {

    /** 知识库在 classpath 下的位置。 */
    static final String KB_PATH = "kb/task-policy.md";

    /** 切段标记：Markdown 二级标题。 */
    static final String SECTION_MARKER = "## ";

    /** 命中段落，带来源元信息，可追踪是哪个片段进了 Context。 */
    public record Segment(String title, String text, double score) { }

    private final VectorStore store;
    private final List<Segment> sections;

    public PolicyKnowledgeBase(EmbeddingModel embeddingModel) {
        this(embeddingModel, loadKbText());
    }

    PolicyKnowledgeBase(EmbeddingModel embeddingModel, String kbText) {
        List<Segment> parsed = parseSections(kbText);
        if (parsed.isEmpty()) {
            throw new IllegalStateException("Knowledge base has no sections: " + KB_PATH);
        }
        this.sections = List.copyOf(parsed);
        this.store = SimpleVectorStore.builder(embeddingModel).build();
        List<Document> documents = new ArrayList<>(parsed.size());
        for (int i = 0; i < parsed.size(); i++) {
            Segment segment = parsed.get(i);
            // 标题是段落内容的一部分，一起入库，检索词命中标题也算命中该段。
            documents.add(new Document(segment.title() + "\n" + segment.text(),
                    Map.of("section_title", segment.title(), "section_index", i)));
        }
        // @FW-CMP [MEMORY] 知识入库：手写版 vs 框架版
        //   手写（references/handwritten-agent-v1/agent-harness/.../KnowledgeStore，大意）：
        //     List<Chunk> chunks = splitter.split(markdown);   // 自己写切分
        //     for (Chunk c : chunks) chunks.save(c);            // 自己写存储（文件/SQLite）
        //     // 检索时自己算相似度或做关键词匹配
        //   框架（下方两行）：
        //     SimpleVectorStore.builder(embeddingModel).build(); + store.add(documents);
        //     // 切分仍自己做，但向量化、存储、相似度检索收进框架
        //   差异：向量化与检索从业务代码消失；代价是"怎么算相似"不可见，
        //   且换向量库要跟着换 VectorStore 实现。
        //   完整版见 docs/comparisons/ch04_spring_ai_advanced.md#2-memory
        this.store.add(documents);
    }

    /** 按语义相似度取回最相关的段落，topK 由调用方定。 */
    public List<Segment> search(String query, int topK) {
        if (query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }
        List<Document> hits = store.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        List<Segment> result = new ArrayList<>(hits.size());
        for (Document hit : hits) {
            String title = String.valueOf(hit.getMetadata().getOrDefault("section_title", "?"));
            Object score = hit.getScore();
            result.add(new Segment(title, hit.getText(), score instanceof Number n ? n.doubleValue() : 0.0));
        }
        return result;
    }

    /** ingest 后共有几段，用于测试断言。 */
    public int sectionCount() {
        return sections.size();
    }

    /** 按 "## 标题" 切段，标题行本身不计入正文。 */
    static List<Segment> parseSections(String markdown) {
        List<Segment> sections = new ArrayList<>();
        String title = null;
        StringBuilder body = new StringBuilder();
        for (String line : markdown.lines().toList()) {
            if (line.startsWith(SECTION_MARKER)) {
                if (title != null) {
                    sections.add(new Segment(title, body.toString().strip(), 0.0));
                    body.setLength(0);
                }
                title = line.substring(SECTION_MARKER.length()).strip();
            } else if (title != null) {
                body.append(line).append('\n');
            }
        }
        if (title != null) {
            sections.add(new Segment(title, body.toString().strip(), 0.0));
        }
        return sections;
    }

    private static String loadKbText() {
        try (InputStream in = new ClassPathResource(KB_PATH).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Read knowledge base failed: " + KB_PATH, e);
        }
    }
}
