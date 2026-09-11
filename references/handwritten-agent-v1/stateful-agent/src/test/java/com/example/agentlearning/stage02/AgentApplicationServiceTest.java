package com.example.agentlearning.stage02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stage02 核心层的确定性测试（用 FakeLlmClient 驱动，不依赖真实模型）。
 *
 * <p>验证：
 * 1. chat 会持久化用户消息（Conversation History）；
 * 2. 偏好被写入 memory 表（Long-term Memory），并在新会话中被检索到；
 * 3. 计划被创建、步骤被执行（Plan）；
 * 4. Agent 状态（goal / status / currentStep）被持久化（Agent State）；
 * 5. 工具失败会触发 Replan；
 * 6. 四类数据存在不同的表里（不能被捏成一张 messages）。
 */
class AgentApplicationServiceTest {

    private Database db;
    private AppComponents c;
    private FakeLlmClient llm;

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    private void setUpWith(FakeLlmClient llm) {
        this.llm = llm;
        this.db = new Database("jdbc:sqlite::memory:");
        this.c = AppComponents.build(llm, db);
    }

    /** 场景1：用户说出 Maven 偏好 → 被保存为记忆，且对话/状态/计划都被持久化。 */
    @Test
    void preferenceIsSavedAsMemoryAndConversationPersisted() {
        FakeLlmClient llm = new FakeLlmClient(
                // 记忆提取：保存 Maven 偏好
                "{\"shouldRemember\":true,\"memoryType\":\"PREFERENCE\",\"content\":\"用户的 Java 学习 Demo 都使用 Maven\"}",
                // 规划：3 步
                "{\"goal\":\"记录 Maven 偏好\",\"steps\":[{\"id\":\"S1\",\"description\":\"确认偏好\"},{\"id\":\"S2\",\"description\":\"保存记忆\"},{\"id\":\"S3\",\"description\":\"确认完成\"}]}",
                // S1..S3 各自 final
                "{\"type\":\"final\",\"answer\":\"好的，已记住 Maven 偏好。\"}",
                "{\"type\":\"final\",\"answer\":\"偏好已保存。\"}",
                "{\"type\":\"final\",\"answer\":\"全部完成。\"}");
        setUpWith(llm);

        Conversation conv = c.conversations.createConversation("会话A");
        ChatResult r = c.agent.chat(conv.id(), "我的学习 Demo 都使用 Maven。");

        // Memory 已保存
        assertTrue(r.memorySaved());
        assertTrue(r.memoryContent().contains("Maven"));
        assertEquals(1, db.countRows("memory"));

        // 对话历史：1 user + 3 assistant（三步骤 final）
        List<StoredMessage> msgs = c.messages.findByConversation(conv.id());
        assertFalse(msgs.isEmpty());
        assertTrue(msgs.stream().anyMatch(m -> "user".equals(m.role()) && m.content().contains("Maven")));

        // 状态已持久化
        assertNotNull(r.runId());
        assertEquals(RunStatus.COMPLETED, r.status());
        assertEquals(3, r.currentStep());
        assertTrue(c.runs.findById(r.runId()).isPresent());

        // 计划已创建且步骤全部 DONE
        Plan plan = r.plan();
        assertNotNull(plan);
        assertEquals(3, plan.steps().size());
        assertTrue(plan.steps().stream().allMatch(s -> s.status() == PlanStepStatus.DONE));

        // 四张表都有对应数据（不能被捏成一张 messages）
        assertTrue(db.countRows("conversation") >= 1);
        assertTrue(db.countRows("message") >= 1);
        assertTrue(db.countRows("agent_run") >= 1);
        assertTrue(db.countRows("plan") >= 1);
        assertTrue(db.countRows("plan_step") >= 1);
        assertTrue(db.countRows("memory") >= 1);
    }

    /** 场景2：偏好在新会话（模拟重启后）被检索到并注入上下文。 */
    @Test
    void preferenceRetrievedInNewConversationAfterReopen() {
        // 先结束一个 Database（模拟关闭/重启），再开新的 Database
        FakeLlmClient llmA = new FakeLlmClient(
                "{\"shouldRemember\":true,\"memoryType\":\"PREFERENCE\",\"content\":\"用户的 Java 学习 Demo 都使用 Maven\"}",
                "{\"goal\":\"记偏好\",\"steps\":[{\"id\":\"S1\",\"description\":\"存\"},{\"id\":\"S2\",\"description\":\"存二\"},{\"id\":\"S3\",\"description\":\"完成\"}]}",
                "{\"type\":\"final\",\"answer\":\"已记住。\"}",
                "{\"type\":\"final\",\"answer\":\"好了。\"}",
                "{\"type\":\"final\",\"answer\":\"完成。\"}");
        Database dbA = new Database("jdbc:sqlite:" + tempDir.resolve("pref.db"));
        AppComponents a = AppComponents.build(llmA, dbA);
        Conversation convA = a.conversations.createConversation("会话A");
        a.agent.chat(convA.id(), "我的学习 Demo 都使用 Maven。");
        dbA.close(); // 模拟程序退出

        // 重启后新 Database
        FakeLlmClient llmB = new FakeLlmClient(
                "{\"shouldRemember\":false}",
                "{\"goal\":\"建项目\",\"steps\":[{\"id\":\"S1\",\"description\":\"建\"},{\"id\":\"S2\",\"description\":\"校\"},{\"id\":\"S3\",\"description\":\"完\"}]}",
                "{\"type\":\"final\",\"answer\":\"用你偏好的 Maven 创建了项目。\"}",
                "{\"type\":\"final\",\"answer\":\"好。\"}",
                "{\"type\":\"final\",\"answer\":\"完成。\"}");
        Database dbB = new Database("jdbc:sqlite:" + tempDir.resolve("pref.db"));
        AppComponents b = AppComponents.build(llmB, dbB);
        Conversation convB = b.conversations.createConversation("会话B");

        ChatResult r = b.agent.chat(convB.id(), "我接下来的学习 Demo 都使用 Maven，帮我创建第一个学习项目。");

        // 新会话检索到了 Maven 记忆
        assertTrue(r.retrievedMemories().stream().anyMatch(m -> m.content().contains("Maven")));
        // 记忆持久化保留（跨 Database）
        assertEquals(1, dbB.countRows("memory"));
        dbB.close();
    }

    /** 场景3：工具调用执行成功，任务是确定性的程序逻辑（落到 task 表）。 */
    @Test
    void planStepsCanCallTools() {
        FakeLlmClient llm = new FakeLlmClient(
                "{\"shouldRemember\":false}",
                "{\"goal\":\"创建学习项目\",\"steps\":[{\"id\":\"S1\",\"description\":\"创建任务\"},{\"id\":\"S2\",\"description\":\"确认\"},{\"id\":\"S3\",\"description\":\"总结\"}]}",
                // S1: 调用 createTask 再 final
                "{\"type\":\"tool_call\",\"tool\":\"createTask\",\"arguments\":{\"title\":\"Agent 学习项目\",\"description\":\"使用 Maven\"},\"decisionSummary\":\"创建项目\"}",
                "{\"type\":\"final\",\"answer\":\"已创建任务。\"}",
                // S2: 调用 listTasks 再 final
                "{\"type\":\"tool_call\",\"tool\":\"listTasks\",\"arguments\":{},\"decisionSummary\":\"查看任务\"}",
                "{\"type\":\"final\",\"answer\":\"任务已确认存在。\"}",
                // S3 final
                "{\"type\":\"final\",\"answer\":\"完成。\"}");
        setUpWith(llm);

        Conversation conv = c.conversations.createConversation("会话A");
        ChatResult r = c.agent.chat(conv.id(), "帮我创建第一个学习项目。");

        assertEquals(RunStatus.COMPLETED, r.status());
        // createTask 真正写入了 task 表
        assertEquals(1, c.taskStore.findAll().size());
        assertEquals("Agent 学习项目", c.taskStore.findAll().get(0).title());
    }

    /** 场景4：工具失败触发 Replan，计划被重规划。 */
    @Test
    void toolFailureTriggersReplan() {
        FakeLlmClient llm = new FakeLlmClient(
                "{\"shouldRemember\":false}",
                "{\"goal\":\"创建项目\",\"steps\":[{\"id\":\"S1\",\"description\":\"创建\"}]}",
                // S1: 调用一个不存在的工具 → 失败 → Replan
                "{\"type\":\"tool_call\",\"tool\":\"deleteAll\",\"arguments\":{},\"decisionSummary\":\"错误调用\"}",
                // Replanner 生成新计划
                "{\"goal\":\"创建项目\",\"steps\":[{\"id\":\"S1\",\"description\":\"改用 createTask\"}]}",
                // S1(new plan): 正确调用 createTask（含 description）
                "{\"type\":\"tool_call\",\"tool\":\"createTask\",\"arguments\":{\"title\":\"任务\",\"description\":\"测试\"},\"decisionSummary\":\"改用正确工具\"}",
                // S1 final
                "{\"type\":\"final\",\"answer\":\"完成。\"}");
        setUpWith(llm);

        Conversation conv = c.conversations.createConversation("会话A");
        ChatResult r = c.agent.chat(conv.id(), "帮我创建一个项目。");

        assertEquals(RunStatus.COMPLETED, r.status());
        assertEquals(1, c.taskStore.findAll().size()); // replan 后 createTask 成功执行
    }

    /** 场景5：Memory 提取为 false 时不保存记忆。 */
    @Test
    void nonPreferenceMessageIsNotSaved() {
        FakeLlmClient llm = new FakeLlmClient(
                "{\"shouldRemember\":false}",
                "{\"goal\":\"普通请求\",\"steps\":[{\"id\":\"S1\",\"description\":\"处理\"}]}",
                "{\"type\":\"final\",\"answer\":\"好的。\"}");
        setUpWith(llm);

        Conversation conv = c.conversations.createConversation("会话A");
        ChatResult r = c.agent.chat(conv.id(), "帮我查一下今天天气。");

        assertFalse(r.memorySaved());
        assertEquals(0, db.countRows("memory"));
    }

    // ---------------- helpers ----------------
}