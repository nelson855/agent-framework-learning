package com.example.agentlearning.stage02;

/**
 * 一个被工具操作的学习/业务任务，对应 {@code task} 表的一行。
 */
public record Task(String id, String title, String description, String status, String createdAt) {
}