package com.example.agentlearning.stage03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProgramValidatorTest {

    private final ProgramValidator validator = new ProgramValidator();

    @Test
    void acceptsValidJson() {
        String raw = """
                {"projectName":"p","planSteps":6,
                 "completedSteps":["S1","S2"],
                 "summary":"完成了计划",
                 "recommendations":["A","B"]}""";
        ProgramValidator.ValidationResult r = validator.validate(raw);
        assertTrue(r.valid());
        assertTrue(r.errors().isEmpty());
        assertEquals("p", r.report().projectName());
    }

    @Test
    void acceptsJsonWithCodeFence() {
        String raw = "```json\n{\"projectName\":\"p\",\"planSteps\":6,"
                + "\"completedSteps\":[\"S1\"],\"summary\":\"s\",\"recommendations\":[\"A\"]}\n```";
        assertTrue(validator.validate(raw).valid());
    }

    @Test
    void rejectsMalformedJson() {
        ProgramValidator.ValidationResult r = validator.validate("not json at all");
        assertFalse(r.valid());
    }

    @Test
    void rejectsMissingRequiredField() {
        String raw = "{\"planSteps\":6,\"completedSteps\":[],\"summary\":\"\",\"recommendations\":[]}";
        ProgramValidator.ValidationResult r = validator.validate(raw);
        assertFalse(r.valid());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("projectName")));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("summary")));
    }

    @Test
    void rejectsEmptyRecommendations() {
        String raw = "{\"projectName\":\"p\",\"planSteps\":6,\"completedSteps\":[\"S1\"],"
                + "\"summary\":\"s\",\"recommendations\":[]}";
        assertFalse(validator.validate(raw).valid());
    }

    @Test
    void rejectsNull() {
        assertFalse(validator.validate(null).valid());
    }
}