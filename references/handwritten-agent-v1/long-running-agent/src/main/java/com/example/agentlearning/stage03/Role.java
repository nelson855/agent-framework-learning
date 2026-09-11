package com.example.agentlearning.stage03;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 消息角色。
 * 序列化到 LLM 请求时使用小写名称。
 */
public enum Role {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    private final String wireName;

    Role(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }
}