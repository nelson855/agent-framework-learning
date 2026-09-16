package com.example.springai.stateful.domain;

/** 任务领域对象。纯业务值对象，不知道模型和工具的存在。 */
public record Task(String id, String title, TaskStatus status) {
}
