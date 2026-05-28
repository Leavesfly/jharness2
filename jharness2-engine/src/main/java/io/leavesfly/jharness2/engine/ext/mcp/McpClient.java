package io.leavesfly.jharness2.engine.ext.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 客户端，基于 McpTransport 抽象与 MCP Server 通信。
 * 支持 Stdio（本地）和 HTTP/SSE（远程）两种传输方式。
 */
public class McpClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String serverName;
    private final McpTransport transport;
    private final AtomicInteger requestId = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private volatile boolean connected;
    private final Object connectLock = new Object();

    public McpClient(String serverName, McpTransport transport) {
        this.serverName = serverName;
        this.transport = transport;
    }

    /**
     * 便捷构造：创建基于 Stdio 传输的 McpClient（兼容原有用法）。
     */
    public McpClient(String serverName, List<String> command, Map<String, String> env) {
        this(serverName, new StdioMcpTransport(serverName, command, env));
    }

    public void connect() throws IOException {
        if (connected && transport.isAlive()) {
            return;
        }
        synchronized (connectLock) {
            if (connected && transport.isAlive()) {
                return;
            }

            transport.onMessage(this::handleMessage);
            transport.connect();

            // MCP Initialize handshake
            ObjectNode initParams = MAPPER.createObjectNode();
            initParams.put("protocolVersion", "2024-11-05");
            initParams.set("capabilities", MAPPER.createObjectNode().set("tools", MAPPER.createObjectNode()));
            ObjectNode clientInfo = MAPPER.createObjectNode();
            clientInfo.put("name", "jharness2");
            clientInfo.put("version", "0.1.0");
            initParams.set("clientInfo", clientInfo);

            JsonNode initResult = sendRequest("initialize", initParams);
            logger.info("MCP server '{}' initialized: {}", serverName,
                    initResult != null ? initResult.path("serverInfo").path("name").asText("unknown") : "unknown");

            sendNotification("notifications/initialized", MAPPER.createObjectNode());
            connected = true;
        }
    }

    /**
     * 确保连接已建立，懒初始化模式。
     */
    public void ensureConnected() {
        if (connected && transport.isAlive()) {
            return;
        }
        try {
            connect();
        } catch (IOException e) {
            throw new RuntimeException("Failed to lazily connect MCP server '" + serverName + "': " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<McpTool> listTools() {
        JsonNode result = sendRequest("tools/list", MAPPER.createObjectNode());
        List<McpTool> tools = new ArrayList<>();
        if (result != null && result.has("tools")) {
            for (JsonNode toolNode : result.get("tools")) {
                String name = toolNode.path("name").asText();
                String desc = toolNode.path("description").asText("");
                Map<String, Object> schema = MAPPER.convertValue(toolNode.path("inputSchema"), Map.class);
                tools.add(new McpTool(name, desc, schema));
            }
        }
        return tools;
    }

    public String callTool(String toolName, Map<String, Object> arguments) {
        ObjectNode params = MAPPER.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", MAPPER.valueToTree(arguments));
        JsonNode result = sendRequest("tools/call", params);
        if (result != null && result.has("content")) {
            JsonNode content = result.get("content");
            if (content.isArray() && !content.isEmpty()) {
                return content.get(0).path("text").asText("");
            }
        }
        return result != null ? result.toString() : "";
    }

    private JsonNode sendRequest(String method, JsonNode params) {
        int id = requestId.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        ObjectNode request = MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);

        try {
            transport.send(request);
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(id);
            logger.warn("MCP request '{}' to '{}' failed: {}", method, serverName, e.getMessage());
            return null;
        }
    }

    private void sendNotification(String method, JsonNode params) {
        ObjectNode notification = MAPPER.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", params);
        try {
            transport.send(notification);
        } catch (IOException e) {
            logger.warn("Failed to send notification '{}' to '{}': {}", method, serverName, e.getMessage());
        }
    }

    private void handleMessage(JsonNode msg) {
        if (msg.has("id") && (msg.has("result") || msg.has("error"))) {
            int id = msg.get("id").asInt();
            CompletableFuture<JsonNode> future = pendingRequests.remove(id);
            if (future == null) {
                return;
            }
            if (msg.has("result")) {
                future.complete(msg.get("result"));
            } else {
                future.completeExceptionally(new RuntimeException(
                        msg.get("error").path("message").asText("Unknown MCP error")));
            }
        } else if (msg.has("method") && !msg.has("id")) {
            // 服务端通知
            String notificationMethod = msg.get("method").asText();
            logger.debug("Received notification from '{}': {}", serverName, notificationMethod);
        }
    }

    public String getServerName() { return serverName; }

    public boolean isRunning() { return transport.isAlive(); }

    @Override
    public void close() {
        connected = false;
        pendingRequests.values().forEach(f -> f.cancel(true));
        pendingRequests.clear();
        transport.close();
        logger.info("MCP client '{}' closed", serverName);
    }
}
