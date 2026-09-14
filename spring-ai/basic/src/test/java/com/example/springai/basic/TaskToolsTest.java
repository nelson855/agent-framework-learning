package com.example.springai.basic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 工具本身测试：直接调 TaskTools，不经过模型。 */
class TaskToolsTest {

    private TaskRepository repository;
    private List<ToolCallTrace> traces;
    private TaskTools tools;

    @BeforeEach
    void setUp() {
        repository = TaskRepository.inMemory();
        traces = new ArrayList<>();
        tools = new TaskTools(new TaskService(repository), traces::add);
    }

    @AfterEach
    void tearDown() {
        repository.close();
    }

    @Test
    void createThenGetTaskThroughTools() {
        String createResult = tools.createTask("学习 Spring AI Advisor");

        assertTrue(createResult.contains("T-1"), "create result should carry the id: " + createResult);
        assertTrue(createResult.contains("OPEN"), "create result should carry the status: " + createResult);

        String getResult = tools.getTask("T-1");

        assertTrue(getResult.contains("学习 Spring AI Advisor"), getResult);
        assertTrue(getResult.contains("OPEN"), getResult);
    }

    @Test
    void getUnknownTaskReturnsNotFoundInsteadOfThrowing() {
        String result = tools.getTask("T-999");

        assertTrue(result.contains("找不到"), result);
    }

    @Test
    void everyCallIsTraced() {
        tools.createTask("学习 Spring AI Advisor");
        tools.getTask("T-1");

        assertEquals(2, traces.size());
        assertEquals("createTask", traces.get(0).toolName());
        assertEquals("getTask", traces.get(1).toolName());
        assertTrue(traces.get(1).result().contains("OPEN"));
    }
}
