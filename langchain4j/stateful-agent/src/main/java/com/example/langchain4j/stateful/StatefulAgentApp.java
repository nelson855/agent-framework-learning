package com.example.langchain4j.stateful;

import com.example.langchain4j.stateful.agent.MemoryService;
import com.example.langchain4j.stateful.agent.PlanExecutor;
import com.example.langchain4j.stateful.agent.Planner;
import com.example.langchain4j.stateful.agent.Replanner;
import com.example.langchain4j.stateful.agent.TaskAgentService;
import com.example.langchain4j.stateful.agent.TaskTools;
import com.example.langchain4j.stateful.config.ChatModelFactory;
import com.example.langchain4j.stateful.config.ModelConfig;
import com.example.langchain4j.stateful.domain.AgentRunRepository;
import com.example.langchain4j.stateful.domain.MemoryRepository;
import com.example.langchain4j.stateful.domain.MessageRepository;
import com.example.langchain4j.stateful.domain.PlanRepository;
import com.example.langchain4j.stateful.domain.TaskRepository;
import com.example.langchain4j.stateful.domain.TaskService;
import com.example.langchain4j.stateful.web.WebServer;
import dev.langchain4j.model.chat.ChatModel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 启动入口：只负责把仓库、服务、Web 组装起来，不实现 Agent 逻辑。
 *
 * <p>@FW-CMP 本类对应手写版 {@code AppComponents.build}（手动 new 客户端、传仓库、组装 Runner）。
 * 手写版和框架版在这里做的是同一件事：装配。框架没有接管装配（本模块没用注入框架），
 * 差异只在"装配出来的是什么"：手写版装的是自研 LlmClient，本模块装的是代理 + ChatModel。
 *
 * <p>完整对比见 {@code docs/comparisons/ch05_langchain4j_stateful.md}。
 */
public final class StatefulAgentApp {

    private StatefulAgentApp() {
    }

    public static void main(String[] args) throws Exception {
        // @FW-CMP [CONFIG] 装配块：不用框架就得自己写这一段
        //   手写（手写版 AppComponents.build）：
        //     Database db = new Database("jdbc:sqlite:data/stage02.db");
        //     LlmClient llm = OpenAiCompatibleLlmClient.fromConfig();
        //     AppComponents app = AppComponents.build(llm, db);
        //   框架（下方）：
        //     // 同样一行行 new：区别是 TaskAgentService 内部藏了一个 AiServices 代理，
        //     // 而手写版的 Runner 是纯手写循环
        //   差异：装配代码量没少，少的是被装配对象内部的循环和解析。
        //   完整版见 docs/comparisons/ch05_langchain4j_stateful.md#8-http
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8081;
        ModelConfig config = ModelConfig.load();
        ChatModel model = config.isConfigured() ? ChatModelFactory.create(config) : ChatModelFactory.offline();
        if (!config.isConfigured()) {
            System.out.println("[警告] 未配置真实模型，使用离线演示模型（仅演示页面功能）。");
            System.out.println("       在仓库根目录放 .env 可接入真实模型。");
        }
        Files.createDirectories(Path.of("data"));
        String jdbcUrl = "jdbc:sqlite:data/langchain4j-stateful.db";
        TaskRepository tasks = new TaskRepository(jdbcUrl);
        MessageRepository messages = new MessageRepository(jdbcUrl);
        MemoryRepository memories = new MemoryRepository(jdbcUrl);
        PlanRepository plans = new PlanRepository(jdbcUrl);
        AgentRunRepository runs = new AgentRunRepository(jdbcUrl);
        TaskTools tools = new TaskTools(new TaskService(tasks));
        TaskAgentService agent =
                new TaskAgentService(model, tools, new MemoryService(memories), messages);
        PlanExecutor executor = new PlanExecutor(
                new Planner(model), new Replanner(model), agent, plans, runs);
        WebServer server = new WebServer(
                port, executor, agent, new MemoryService(memories), messages, plans, runs);
        server.start();
        System.out.println("LangChain4j Stateful Agent 启动: http://localhost:" + port);
        System.out.println("页面左侧: Chat（对话）");
        System.out.println("页面右侧: Current State（运行状态） / Plan（计划） / Retrieved Memory（长期记忆） / Recent Tool Calls（工具调用）");
    }
}
