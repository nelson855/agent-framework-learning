package com.example.springai.stateful.web.dto;

/** 单条消息视图。 */
public record MessageDto(long id, String role, String content) {
}
