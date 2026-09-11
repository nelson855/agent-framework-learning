package com.example.agentlearning.stage02;

/**
 * 一条消息：角色 + 内容。
 */
public record Message(Role role, String content) {

    public static Message system(String content) {
        return new Message(Role.SYSTEM, content);
    }

    public static Message user(String content) {
        return new Message(Role.USER, content);
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }
}