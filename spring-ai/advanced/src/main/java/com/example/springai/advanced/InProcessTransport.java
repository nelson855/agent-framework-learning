package com.example.springai.advanced;

/**
 * 同进程传输：测试用，不启子进程，直接调 {@link TaskMcpServer#handleLine}。
 *
 * <p>协议与 {@link StdioProcessTransport} 完全一致（一样走 JSON 文本），
 * 因此用它跑通的测试结论对 stdio 同样成立；区别只在"跨没跨进程边界"。
 */
public class InProcessTransport implements JsonRpcTransport {

    private final TaskMcpServer server;
    private boolean closed;

    public InProcessTransport(TaskMcpServer server) {
        this.server = server;
    }

    @Override
    public synchronized String exchange(String requestLine) {
        if (closed) {
            throw new TaskMcpClient.McpException("transport closed");
        }
        return server.handleLine(requestLine);
    }

    @Override
    public synchronized void close() {
        closed = true;
    }
}
