package com.example.springai.stateful.domain;

/** 一次会话。纯业务值对象，只认 userId，不感知模型。 */
public record Conversation(String id, String userId) {
}
