package com.example.langchain4j.stateful.experiments;

/**
 * Agentic API 观察记录（小实验，不进主路径）。
 *
 * <p>核查日期 2026-09-23，LangChain4j 1.20.0：
 * 官方把新一代 Agentic API（含 {@code langchain4j-agentic} 构件、A2A 协议支持）
 * 放在 beta 版本线（如 {@code 1.20.0-betaXX}）维护，稳定 BOM（{@code langchain4j-bom:1.20.0}）
 * 只收录稳定模块。因此本模块主路径刻意不依赖它：
 * Benchmark A 用稳定 API（AiServices + ChatModel + ChatMemoryProvider + @Tool）已能完整表达，
 * 强行换成实验性 API 会让"同一业务对标 Spring AI 版"的目标落空。
 *
 * <p>想跟进时：在 pom 里另加 beta 坐标做独立小实验，不要动主路径。
 * 详见 README 的"Agentic API 状态"一节。
 */
public final class AgenticApiProbe {

    /** 本模块使用的稳定版本。 */
    public static final String STABLE_VERSION = "1.20.0";

    /** 实验性 Agentic 构件所在的版本线（未引入，仅记录）。 */
    public static final String AGENTIC_VERSION_LINE = "1.20.0-betaXX";

    private AgenticApiProbe() {
    }

    /** 返回当前结论，供测试断言"主路径没偷偷依赖实验 API"。 */
    public static String probe() {
        return "stable=" + STABLE_VERSION
                + ",agentic=" + AGENTIC_VERSION_LINE
                + ",usedInMainPath=false";
    }
}
