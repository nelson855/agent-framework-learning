package com.example.langchain4j.stateful;

import com.example.langchain4j.stateful.experiments.AgenticApiProbe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Agentic 小实验：主路径没有依赖实验性 API，只留观察记录。 */
class AgenticApiProbeTest {

    @Test
    void mainPathDoesNotUseExperimentalApi() {
        assertTrue(AgenticApiProbe.probe().contains("usedInMainPath=false"));
    }
}
