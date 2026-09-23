package com.example.springai.advanced;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * MCP Server 独立进程入口：stdin 读一行 JSON-RPC，stdout 回一行响应。
 *
 * <p>Agent 端用 {@link StdioProcessTransport} fork 本类，
 * 两边只通过管道里的 JSON 文本交互，这就是教材说的"跨越进程边界"。
 */
public class McpServerMain {

    public static void main(String[] args) throws Exception {
        TaskMcpServer server = TaskMcpServer.demo();
        BufferedReader stdin = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = stdin.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            System.out.println(server.handleLine(line));
            System.out.flush();
        }
    }
}
