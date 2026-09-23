package com.example.springai.advanced;

/**
 * JSON-RPC 2.0 传输接口：一次发一行 JSON 请求，收一行 JSON 响应。
 *
 * <p>MCP 协议本身不关心字节怎么走，stdio 管道和同进程直调都实现这个接口。
 * 教学上这正好说明：换传输不换协议，边界变化不影响工具语义。
 */
public interface JsonRpcTransport extends AutoCloseable {

    /**
     * 发送一行 JSON-RPC 请求，阻塞等待响应行。
     *
     * @param requestLine 单行 JSON，如 {@code {"jsonrpc":"2.0","id":1,"method":"tools/list"}}
     * @return 单行 JSON 响应
     * @throws McpException 传输失败（进程崩溃、管道断开、超时）时抛出
     */
    String exchange(String requestLine);

    @Override
    void close();
}
