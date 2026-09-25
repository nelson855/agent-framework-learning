package com.example.langchain4j.stateful;

import com.example.langchain4j.stateful.agent.Planner;
import com.example.langchain4j.stateful.support.FakeChatModel;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Planner：模型输出的步骤 JSON 被拆成清单，空输出兜底为空表。 */
class PlannerTest {

    @Test
    void parsesStepsJson() {
        Planner planner = new Planner(
                FakeChatModel.answering("{\"steps\":[\"第一步\",\"第二步\",\"第三步\"]}"));

        List<String> steps = planner.draftSteps("整理本周任务");

        assertEquals(List.of("第一步", "第二步", "第三步"), steps);
    }

    @Test
    void toleratesSurroundingText() {
        Planner planner = new Planner(FakeChatModel.answering(
                "好的，这是计划：{\"steps\":[\"第一步\",\"第二步\"]}，请执行。"));

        List<String> steps = planner.draftSteps("整理本周任务");

        assertEquals(List.of("第一步", "第二步"), steps);
    }

    @Test
    void blankOutputGivesEmptyPlan() {
        Planner planner = new Planner(FakeChatModel.answering(""));

        assertTrue(planner.draftSteps("整理本周任务").isEmpty());
    }
}
