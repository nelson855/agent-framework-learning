package com.example.agentlearning.stage04;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * ToolCall 的 JSON 序列化/反序列化（审批请求与 Checkpoint 落库用）。
 */
final class ToolJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolJson() {
    }

    static String toJson(ToolCall call) {
        try {
            return MAPPER.writeValueAsString(call);
        } catch (IOException e) {
            throw new IllegalStateException("序列化 ToolCall 失败", e);
        }
    }

    static ToolCall fromJson(String json) {
        try {
            return MAPPER.readValue(json, ToolCall.class);
        } catch (IOException e) {
            throw new IllegalStateException("反序列化 ToolCall 失败", e);
        }
    }
}