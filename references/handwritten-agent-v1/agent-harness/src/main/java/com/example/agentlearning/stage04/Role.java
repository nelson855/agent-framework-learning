package com.example.agentlearning.stage04;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 消息角色。序列化到 LLM 请求时使用小写名称。
 *
 * <p>{@code @JsonValue} 负责输出 {@code "user"} 等 wire 名；{@code @JsonCreator} 负责
 * 从 wire 名反序列化回来 —— {@code HITLAgentLoop} 会把 context(消息历史)作为 Checkpoint 落地
 * SQLite 并在批准后读回，两个方向都要能转换。
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

    @JsonCreator
    public static Role fromWire(String name) {
        for (Role role : values()) {
            if (role.wireName.equals(name)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知角色: " + name);
    }
}