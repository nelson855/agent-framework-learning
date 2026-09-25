package com.example.langchain4j.stateful.agent;

import com.example.langchain4j.stateful.domain.AgentRun;
import com.example.langchain4j.stateful.domain.AgentRunRepository;
import com.example.langchain4j.stateful.domain.Plan;
import com.example.langchain4j.stateful.domain.PlanRepository;
import com.example.langchain4j.stateful.domain.PlanStep;
import com.example.langchain4j.stateful.domain.PlanStepStatus;
import com.example.langchain4j.stateful.domain.RunStatus;
import java.util.List;

/**
 * 外层计划循环：拆步骤 → 逐步调内层对话 → 记状态。
 *
 * <p>@FW-CMP [TOOL_LOOP] 外层 Plan 循环框架没有概念，只能自建
 *   手写（手写版 StatefulAgentRunner.run）：
 *     for (PlanStep step : plan.steps()) {        // 外层：推进计划
 *         for (i < MAX_STEPS_PER_STEP) {          // 内层：ReAct 小循环
 *             模型 → 工具 → 模型 → ...
 *         }
 *     }
 *   框架（本类 + TaskAgentService）：
 *     // AiServices 代理只接管内层小循环（一次 chat()）；
 *     // 外层"哪步走完走哪步、失败了重规划、状态落库"没有框架概念，
 *     // 本类就是那个外层循环
 *   差异：框架不是"接管一切"，而是"在特定层接管"；
 *   本章最重要的分界线就在这里：内层代理管，外层自己写。
 *   Spring AI 版是同一个分界线（ToolCallingAdvisor 管内层），两家一致。
 *   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#4-plan-state
 */
public class PlanExecutor {

    private final Planner planner;
    private final Replanner replanner;
    private final TaskAgentService agent;
    private final PlanRepository plans;
    private final AgentRunRepository runs;

    public PlanExecutor(
            Planner planner,
            Replanner replanner,
            TaskAgentService agent,
            PlanRepository plans,
            AgentRunRepository runs) {
        this.planner = planner;
        this.replanner = replanner;
        this.agent = agent;
        this.plans = plans;
        this.runs = runs;
    }

    /** 最多重规划 2 次，一直失败就认输，避免无限循环。 */
    private static final int MAX_REPLANS = 2;

    /** 执行一个目标：建运行记录 → 拆计划 → 逐步执行 → 状态落库。返回最终的运行记录。 */
    public AgentRun execute(String conversationId, String userId, String goal) {
        AgentRun run = runs.createRun(conversationId, goal);
        List<String> steps = planner.draftSteps(goal);
        Plan plan = plans.createPlan(run.runId(), steps);
        int replans = 0;
        int index = 0;
        while (index < plan.steps().size()) {
            PlanStep step = plan.steps().get(index);
            plans.updateStepStatus(plan.id(), step.id(), PlanStepStatus.RUNNING, null);
            try {
                agent.chat(conversationId, userId, step.description());
            } catch (RuntimeException e) {
                plans.updateStepStatus(plan.id(), step.id(), PlanStepStatus.FAILED, e.getMessage());
                if (replans >= MAX_REPLANS) {
                    runs.updateStatus(run.runId(), RunStatus.FAILED, index);
                    throw e;
                }
                replans++;
                List<String> fresh = replanner.replan(goal, step.description(), e.getMessage());
                plan = plans.createPlan(run.runId(), fresh);
                index = 0;
                continue;
            }
            plans.updateStepStatus(plan.id(), step.id(), PlanStepStatus.DONE, null);
            index++;
            runs.updateStatus(run.runId(), RunStatus.RUNNING, index);
        }
        runs.updateStatus(run.runId(), RunStatus.DONE, index);
        return runs.findById(run.runId()).orElseThrow();
    }
}
