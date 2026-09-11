package com.example.agentlearning.stage02;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Stage 02 CLI 入口。
 *
 * <p>复用与 Web 完全相同的核心 {@link AgentApplicationService}，只是交互层换成控制台。
 * 因此 CLI 与 Web 不复制两套 Agent 逻辑。
 *
 * <p>支持：
 * <ul>
 *   <li>{@code --demo}：跑一遍"Session A 保存 Maven 偏好 → 退出 → Session B 检索并执行"的完整演示（离线的 FakeLlmClient）；</li>
 *   <li>默认：交互模式。命令 {@code /chat new}、{@code /chat <id>}、{@code /state}、
 *       {@code /plan}、{@code /memory}、{@code /help}、{@code /exit}。</li>
 * </ul>
 */
public final class Main {

    private static final String DB_URL = "jdbc:sqlite:data/stage02.db";

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && "--demo".equals(args[0])) {
            runDemo();
        } else {
            runInteractive();
        }
    }

    // ---------------- 离线演示 ----------------

    private static void runDemo() throws IOException {
        System.out.println("==== Stage02 演示：Conversation / State / Plan / Memory ====");
        System.out.println();

        FakeLlmClient llm = demoScript();

        System.out.println("---- Session A：用户说出「以后我的学习 Demo 都使用 Maven」 ----");
        Database dbA = openDatabase();
        AppComponents a = AppComponents.build(llm, dbA);
        Conversation sessionA = a.conversations.createConversation("Session A");
        chatAndPrint(a, sessionA.id(), "我的学习 Demo 都使用 Maven。");
        dbA.close();

        System.out.println();
        System.out.println("==== 进程退出：数据库已关闭（内存对话全部丢弃） ====");
        System.out.println();

        System.out.println("---- Session B：新的会话，靠 memory 表回忆起 Maven 偏好 ----");
        Database dbB = openDatabase();
        AppComponents b = AppComponents.build(llm, dbB);
        Conversation sessionB = b.conversations.createConversation("Session B");
        chatAndPrint(b, sessionB.id(), "我接下来的学习 Demo 都使用 Maven，帮我创建第一个学习项目。");
        b.db.close();

        System.out.println();
        System.out.println("==== 演示结束：Maven 偏好跨会话保留在 memory 表 ====");
    }

    private static void chatAndPrint(AppComponents c, String conversationId, String input) {
        System.out.println(">>> 用户: " + input);
        ChatResult result = c.agent.chat(conversationId, input);
        System.out.println("MEMORY: " + (result.memorySaved() ? "已保存 " + result.memoryContent() : "未保存"));
        System.out.println("STATE:  " + result.runId() + " / " + result.status() + " / step=" + result.currentStep());
        System.out.println("PLAN:   " + (result.plan() != null ? result.plan().goal() : "-"));
        System.out.println("AGENT:  " + result.answer());
        System.out.println();
    }

    private static FakeLlmClient demoScript() {
        return new FakeLlmClient(
                // Session A：消息1 - 记忆提取(保存 Maven 偏好)
                "{\"shouldRemember\":true,\"memoryType\":\"PREFERENCE\",\"content\":\"用户的 Java 学习 Demo 都使用 Maven\"}",
                // Session A：消息1 - 规划（3 步）
                "{\"goal\":\"记录 Maven 偏好\",\"steps\":[{\"id\":\"S1\",\"description\":\"确认 Maven 偏好\"},{\"id\":\"S2\",\"description\":\"保存偏好到记忆\"},{\"id\":\"S3\",\"description\":\"确认已保存\"}]}",
                // Session A：消息1 - S1 执行
                "{\"type\":\"final\",\"answer\":\"好的，已记住：以后你的学习 Demo 都使用 Maven。\"}",
                // Session A：消息1 - S2
                "{\"type\":\"final\",\"answer\":\"偏好已保存。\"}",
                // Session A：消息1 - S3
                "{\"type\":\"final\",\"answer\":\"已完成全部计划。\"}",
                // Session B：消息2 - 记忆提取（不保存，这是任务指令）
                "{\"shouldRemember\":false}",
                // Session B：消息2 - 规划（3 步：用 Maven 创建项目）
                "{\"goal\":\"创建学习项目\",\"steps\":[{\"id\":\"S1\",\"description\":\"创建项目任务\"},{\"id\":\"S2\",\"description\":\"列出已创建任务\"},{\"id\":\"S3\",\"description\":\"总结配置理由\"}]}",
                // Session B：消息2 - S1：调用 createTask
                "{\"type\":\"tool_call\",\"tool\":\"createTask\",\"arguments\":{\"title\":\"Agent 学习项目\",\"description\":\"使用 Maven 构建\"},\"decisionSummary\":\"创建第一个学习项目\"}",
                // Session B：消息2 - S1 final
                "{\"type\":\"final\",\"answer\":\"已创建使用 Maven 的学习项目任务。\"}",
                // Session B：消息2 - S2：调用 listTasks
                "{\"type\":\"tool_call\",\"tool\":\"listTasks\",\"arguments\":{},\"decisionSummary\":\"查看当前任务\"}",
                // Session B：消息2 - S2 final
                "{\"type\":\"final\",\"answer\":\"已确认任务已创建。\"}",
                // Session B：消息2 - S3 final（结合记忆说明为什么用 Maven）
                "{\"type\":\"final\",\"answer\":\"因为你的偏好是使用 Maven，所以我用它初始化了学习项目，代码如下：application/pom.xml with Maven。\"}");
    }

    // ---------------- 交互模式 ----------------

    private static void runInteractive() throws IOException {
        Database db = openDatabase();
        LlmClient llm = OpenAiCompatibleLlmClient.isConfigured()
                ? OpenAiCompatibleLlmClient.fromConfig()
                : offlineInteractiveLlm();
        if (!OpenAiCompatibleLlmClient.isConfigured()) {
            System.out.println("[警告] 未配置 LLM_BASE_URL / LLM_API_KEY / LLM_MODEL，使用离线脚本模型（仅演示）。");
            System.out.println("       在仓库根目录放 .env 可接入真实模型。");
        }
        AppComponents c = AppComponents.build(llm, db);

        System.out.println("=== stage02-stateful-agent: 有状态控制台助手（CLI） ===");
        System.out.println("命令: /chat new|/chat <id>|/state|/plan|/memory|/help|/exit");
        System.out.println("其他输入当作用户消息（会先提取记忆、生成计划，再由 Agent 执行）。\n");
        printHelp();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String conversationId = null;

        String line;
        while ((line = reader.readLine()) != null) {
            String input = line.trim();
            if (input.isEmpty()) {
                continue;
            }
            if (input.equalsIgnoreCase("/exit")) {
                break;
            }
            if (input.equalsIgnoreCase("/help")) {
                printHelp();
                continue;
            }
            if (input.equals("/chat new")) {
                Conversation created = c.conversations.createConversation(
                        "会话 " + (c.conversations.listConversations().size() + 1));
                conversationId = created.id();
                System.out.println("已创建并切换到会话: " + created.id());
                continue;
            }
            if (input.startsWith("/chat ")) {
                String id = input.substring("/chat ".length()).trim();
                Optional<Conversation> found = c.conversations.findConversationById(id);
                if (found.isPresent()) {
                    conversationId = id;
                    System.out.println("已切换到会话: " + id);
                } else {
                    System.out.println("未找到会话: " + id);
                }
                continue;
            }
            if (input.equalsIgnoreCase("/state") || input.equalsIgnoreCase("/plan") || input.equalsIgnoreCase("/memory")) {
                printState(c, conversationId);
                continue;
            }
            if (conversationId == null) {
                System.out.println("请先 /chat new 创建会话。");
                continue;
            }
            chatAndPrint(c, conversationId, input);
        }
        db.close();
        System.out.println("bye");
    }

    private static void printState(AppComponents c, String conversationId) {
        if (conversationId == null) {
            System.out.println("还没有会话。");
            return;
        }
        c.runs.findLatestByConversation(conversationId).ifPresentOrElse(run -> {
            System.out.println("run:      " + run.runId());
            System.out.println("goal:     " + run.goal());
            System.out.println("status:   " + run.status());
            System.out.println("step:     " + run.currentStep());
            c.planRepo.findLatestByRun(run.runId()).ifPresent(plan -> {
                System.out.println("plan:     " + plan.goal());
                for (PlanStep step : plan.steps()) {
                    System.out.println("          [" + step.id() + "] " + step.description() + " (" + step.status() + ")");
                }
            });
        }, () -> System.out.println("该会话还没有 Agent 运行。"));
        System.out.println("memory:");
        List<Memory> mems = c.memories.findAll();
        if (mems.isEmpty()) {
            System.out.println("  （无）");
        } else {
            for (Memory m : mems) {
                System.out.println("  - [" + m.type() + "] " + m.content());
            }
        }
    }

    private static void printHelp() {
        System.out.println("""
                命令：
                  /chat new          创建新会话并切换过去
                  /chat <id>         切换到已有会话（恢复对话历史）
                  /state /plan       查看最近一次运行的状态与计划
                  /memory            查看已保存的长期记忆
                  /help              显示帮助
                  /exit              退出
                其他输入：作为用户消息执行（提取记忆 + 规划 + Agent 执行）。
                演示: mvn -pl stages/stage02-stateful-agent compile exec:java -Dexec.args="--demo"
                网页: 运行 WebMain 后浏览器打开 http://localhost:8080
                """);
    }

    private static FakeLlmClient offlineInteractiveLlm() {
        return new FakeLlmClient(
                "{\"shouldRemember\":false}",
                "{\"goal\":\"离线演示计划\",\"steps\":[{\"id\":\"S1\",\"description\":\"示范步骤一\"},{\"id\":\"S2\",\"description\":\"示范步骤二\"},{\"id\":\"S3\",\"description\":\"示范步骤三\"}]}",
                "{\"type\":\"final\",\"answer\":\"（离线模式）未配置真实模型，无法智能回复；但你仍可观察 Conversation/State/Plan/Memory 的持久化。\"}",
                "{\"type\":\"final\",\"answer\":\"（步骤二完成）\"}",
                "{\"type\":\"final\",\"answer\":\"（离线演示全部步骤已完成）\"}");
    }

    private static Database openDatabase() throws IOException {
        Files.createDirectories(Path.of("data"));
        return new Database(DB_URL);
    }
}