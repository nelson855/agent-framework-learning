package com.example.springai.stateful.web;

import com.example.springai.stateful.agent.PlanExecutor;
import com.example.springai.stateful.domain.AgentRun;
import com.example.springai.stateful.web.dto.ChatRequestDto;
import com.example.springai.stateful.web.dto.ChatResponseDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话入口：一次 POST 跑完整个计划（同步等结果）。
 *
 * <p>注意这个同步语义：请求线程里把 PlanExecutor 跑完才返回。
 * 演示够用；真要给多人用，得换成"先返回运行编号、后台慢慢跑"。
 */
@RestController
@RequestMapping("/conversations/{id}/chat")
public class ChatController {

    private final PlanExecutor executor;

    public ChatController(PlanExecutor executor) {
        this.executor = executor;
    }

    @PostMapping
    public ChatResponseDto chat(
            @PathVariable("id") String conversationId, @RequestBody ChatRequestDto request) {
        AgentRun run = executor.execute(conversationId, request.userId(), request.goal());
        return new ChatResponseDto(run.runId());
    }
}
