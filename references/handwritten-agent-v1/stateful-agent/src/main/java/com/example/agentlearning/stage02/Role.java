package com.example.agentlearning.stage02;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 消息角色。序列化到 LLM 请求时使用小写名称（system / user / assistant）。
 */
public enum Role {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    private final String wireName;

    Role(String wireName) {
        this.wireName = wireName;
    }

    /** Jackson 序列化枚举时输出小写 wire 名，而不是默认的大写枚举名。 */
    @JsonValue
    public String wireName() {
        return wireName;
    }
}