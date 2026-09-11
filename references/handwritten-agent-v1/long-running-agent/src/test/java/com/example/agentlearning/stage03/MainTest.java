package com.example.agentlearning.stage03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void reportScriptStartsWithInvalidJson() {
        ScriptedLlmClient report = (ScriptedLlmClient) Main.reportScript();
        String first = report.chat(List.of(Message.user("x"))).content();
        assertTrue(first.contains("缺少必要字段"));
    }

    @Test
    void evaluatorScriptRejectsThenPasses() {
        ScriptedLlmClient eval = (ScriptedLlmClient) Main.evaluatorScript();
        String first = eval.chat(List.of(Message.user("x"))).content();
        String second = eval.chat(List.of(Message.user("x"))).content();
        assertTrue(first.contains("\"pass\":false"));
        assertTrue(second.contains("\"pass\":true"));
    }
}