package com.example.langchain4j.stateful.domain;

/** 一条长期记忆：某个用户的键值偏好。纯业务值对象。 */
public record Memory(long id, String userId, String memoryKey, String memoryValue) {
}
