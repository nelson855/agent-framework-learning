package com.example.springai.stateful.domain;

/**
 * 一条 UI History 消息：用户在界面上看到的完整流水账。
 *
 * <p>注意它和框架 ChatMemory 的区别：这里落 SQLite，重启不丢；
 * ChatMemory 是内存窗口，重启就丢。两者同时存在，各管各的。
 */
public record Message(long id, String conversationId, String role, String content) {
}
