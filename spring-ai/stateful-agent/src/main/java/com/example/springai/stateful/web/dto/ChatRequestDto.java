package com.example.springai.stateful.web.dto;

/** 发起对话请求：谁的哪个目标。 */
public record ChatRequestDto(String userId, String goal) {
}
