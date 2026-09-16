package com.example.springai.stateful.web;

import com.example.springai.stateful.domain.AgentRun;
import com.example.springai.stateful.domain.AgentRunRepository;
import com.example.springai.stateful.domain.PlanRepository;
import com.example.springai.stateful.web.dto.RunStateDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行状态查询：跑到哪一步、每步什么状态。
 *
 * <p>@FW-CMP [TRACE] 可观测性没有框架方案，查自建表
 *   手写（手写版 TraceService / Evaluator）：
 *     // 每次工具调用记 trace，单独的 evaluator 复盘
 *   框架（本类）：
 *     // Spring AI 的观测是 Micrometer 指标，不管"计划走到哪一步"这种业务进度；
 *     // 进度查询只能读自己的 agent_run + plan_step 表
 *   差异：业务进度可观测性框架不包，想在界面上显示"第 2/3 步"，
 *   表和接口都得自己建。本章没接 Micrometer，要看模型 token 级指标得另起一轮。
 *   完整版见 docs/comparisons/ch03_spring_ai_stateful.md#9-trace
 */
@RestController
@RequestMapping("/runs")
public class StateController {

    private final AgentRunRepository runs;
    private final PlanRepository plans;

    public StateController(AgentRunRepository runs, PlanRepository plans) {
        this.runs = runs;
        this.plans = plans;
    }

    @GetMapping("/{runId}")
    public RunStateDto getState(@PathVariable("runId") String runId) {
        AgentRun run = runs.findById(runId).orElseThrow();
        var planDtos = plans.findByRunId(runId).stream()
                .map(p -> new RunStateDto.PlanDto(
                        p.id(),
                        p.steps().stream()
                                .map(s -> new RunStateDto.StepDto(
                                        s.id(), s.description(), s.status().name()))
                                .toList()))
                .toList();
        return new RunStateDto(
                run.runId(), run.goal(), run.status().name(), run.currentStep(), planDtos);
    }
}
