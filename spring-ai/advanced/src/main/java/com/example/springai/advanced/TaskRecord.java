package com.example.springai.advanced;

import java.util.Objects;

/** 任务记录：MCP Server 的后端数据，供 get_task_status 查询。 */
public record TaskRecord(String id, String title, TaskStatus status) {

    public TaskRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(status, "status");
    }
}
