package io.leavesfly.jharness2.engine.ext.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * MCP 传输层抽象接口。
 * 支持 Stdio（本地子进程）和 HTTP/SSE（远程服务器）两种标准传输方式。
 */
public interface McpTransport extends AutoCloseable {

    /**
     * 建立连接。
     */
    void connect() throws IOException;

    /**
     * 发送一条 JSON-RPC 消息到 MCP Server。
     */
    void send(JsonNode message) throws IOException;

    /**
     * 设置消息接收回调，当从 MCP Server 收到消息时触发。
     */
    void onMessage(Consumer<JsonNode> handler);

    /**
     * 连接是否存活。
     */
    boolean isAlive();

    /**
     * 关闭连接并释放资源。
     */
    @Override
    void close();
}
