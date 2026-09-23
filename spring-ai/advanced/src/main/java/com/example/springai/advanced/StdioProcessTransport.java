package com.example.springai.advanced;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * stdio 进程传输：fork 一个 {@code McpServerMain} 子进程，用 stdin/stdout 传 JSON-RPC。
 *
 * <p>@FW-CMP [HTTP] 传输边界：手写版 vs 远程工具
 * 手写（agent-harness/OpenAiCompatibleLlmClient.chat）：
 * 手写版只和模型服务打过 HTTP，工具全是同进程方法调用，没有"调工具还要走管道"这一说；
 * 本类是本章第一次出现"调个工具要跨进程"的传输代码：启子进程、管 stdin/stdout、
 * 一行 JSON 发过去、一行 JSON 读回来。
 * 框架（无，Spring AI MCP Starter 也是类似的进程/HTTP 客户端，本章手写是刻意的）：
 * 官方有 spring-ai-starter-mcp-client，但它要求 Spring Boot 自动装配，
 * 本章走纯 Java 手工装配路线，所以传输层自己写 60 行，换来"协议很薄、边界很厚"看得见。
 * 差异：工具调用从"方法调用语义"变成"请求响应语义"，多了进程启停、管道断开、超时三类新故障；
 * 代价：exchange 必须同步加锁，一次只能飞一个请求（教学简化，不做多路复用）。
 * 完整版见 docs/comparisons/ch04_spring_ai_advanced.md#5-transport
 */
public class StdioProcessTransport implements JsonRpcTransport {

    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private boolean closed;

    /** fork 子进程并握住它的 stdin/stdout。 */
    public static StdioProcessTransport spawn() {
        String classpath = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElse("java"),
                "-cp", classpath, McpServerMain.class.getName()));
        try {
            Process process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            return new StdioProcessTransport(process);
        } catch (IOException e) {
            throw new TaskMcpClient.McpException("spawn MCP server failed: " + command, e);
        }
    }

    StdioProcessTransport(Process process) {
        this.process = process;
        this.stdin = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.stdout = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public synchronized String exchange(String requestLine) {
        if (closed) {
            throw new TaskMcpClient.McpException("transport closed");
        }
        if (!process.isAlive()) {
            throw new TaskMcpClient.McpException(
                    "MCP server process died, exit=" + process.exitValue());
        }
        try {
            stdin.write(requestLine);
            stdin.newLine();
            stdin.flush();
            String response = stdout.readLine();
            if (response == null) {
                throw new TaskMcpClient.McpException("MCP server closed stdout");
            }
            return response;
        } catch (IOException e) {
            throw new TaskMcpClient.McpException("stdio exchange failed", e);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            stdin.close();
        } catch (IOException ignored) {
            // 关闭时管道可能已断，无需再报错
        }
        try {
            stdout.close();
        } catch (IOException ignored) {
            // 同上
        }
        process.destroy();
    }
}
