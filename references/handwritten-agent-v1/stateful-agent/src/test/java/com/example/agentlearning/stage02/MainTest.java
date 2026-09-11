package com.example.agentlearning.stage02;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void mainCreatesComponents() {
        // 最小 smoke test：验证 AppComponents 能正常装配
        Database db = new Database("jdbc:sqlite::memory:");
        FakeLlmClient llm = new FakeLlmClient(
                "{\"shouldRemember\":false}",
                "{\"goal\":\"测试\",\"steps\":[{\"id\":\"S1\",\"description\":\"测试\"}]}",
                "{\"type\":\"final\",\"answer\":\"ok\"}");
        AppComponents c = AppComponents.build(llm, db);
        assertNotNull(c.agent);
        assertNotNull(c.runner);
        db.close();
    }
}