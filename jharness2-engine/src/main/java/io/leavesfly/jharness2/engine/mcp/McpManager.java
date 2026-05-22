package io.leavesfly.jharness2.engine.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.engine.tool.ToolRegistry;
import io.leavesfly.jharness2.engine.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class McpManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpManager.class);
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();

    public void addServer(String name, List<String> command, Map<String, String> env) {
        McpClient client = new McpClient(name, command, env);
        clients.put(name, client);
    }

    /**
     * 立即连接所有 MCP 服务器（阻塞式，适用于预热场景）。
     */
    public void connectAll() {
        for (Map.Entry<String, McpClient> entry : clients.entrySet()) {
            try {
                entry.getValue().connect();
                logger.info("Connected to MCP server: {}", entry.getKey());
            } catch (IOException e) {
                logger.error("Failed to connect MCP server '{}': {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * 懒连接模式：仅记录服务器，不立即建立连接。
     * 连接将在首次工具调用时按需建立。
     */
    public void prepareLazyConnect() {
        logger.info("MCP servers registered for lazy connect: {}", clients.keySet());
    }

    /**
     * 将所有已连接的 MCP 服务器的工具注册到 ToolRegistry（使用 BaseTool 适配器）。
     * 要求服务器已连接。
     */
    public int registerToolsTo(ToolRegistry registry) {
        int count = 0;
        for (McpClient client : clients.values()) {
            if (!client.isRunning()) continue;
            List<McpTool> tools = client.listTools();
            for (McpTool tool : tools) {
                String prefixedName = "mcp_" + client.getServerName() + "_" + tool.getName();
                registry.register(new McpToolAdapter(prefixedName, tool, client));
                count++;
            }
        }
        logger.info("Registered {} MCP tools from {} servers", count, clients.size());
        return count;
    }

    /**
     * 异步连接所有 MCP 服务器，连接完成后自动将工具注册到 ToolRegistry。
     * 引擎创建时不阻塞，工具在后台就绪后即可使用。
     */
    public CompletableFuture<Integer> connectAndRegisterAsync(ToolRegistry registry) {
        return CompletableFuture.supplyAsync(() -> {
            int totalTools = 0;
            for (Map.Entry<String, McpClient> entry : clients.entrySet()) {
                try {
                    entry.getValue().connect();
                    logger.info("Async connected to MCP server: {}", entry.getKey());
                    List<McpTool> tools = entry.getValue().listTools();
                    for (McpTool tool : tools) {
                        String prefixedName = "mcp_" + entry.getKey() + "_" + tool.getName();
                        registry.register(new McpToolAdapter(prefixedName, tool, entry.getValue()));
                        totalTools++;
                    }
                } catch (Exception e) {
                    logger.error("Async connect MCP server '{}' failed: {}", entry.getKey(), e.getMessage());
                }
            }
            logger.info("Async registered {} MCP tools from {} servers", totalTools, clients.size());
            return totalTools;
        });
    }

    public McpClient getClient(String name) {
        return clients.get(name);
    }

    @Override
    public void close() {
        clients.values().forEach(McpClient::close);
        clients.clear();
    }

    /**
     * MCP 工具适配器 - 将 MCP 服务器的工具封装为 BaseTool。
     */
    private static class McpToolAdapter extends BaseTool<Map> {
        private static final ObjectMapper MAPPER = new ObjectMapper();
        private final String name;
        private final McpTool mcpTool;
        private final McpClient client;

        McpToolAdapter(String name, McpTool mcpTool, McpClient client) {
            this.name = name;
            this.mcpTool = mcpTool;
            this.client = client;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return mcpTool.getDescription(); }

        @Override
        @SuppressWarnings("unchecked")
        public Class<Map> getInputClass() { return Map.class; }

        @Override
        protected Map<String, Object> buildParametersSchema() {
            return mcpTool.getInputSchema() != null ? mcpTool.getInputSchema() :
                    Map.of("type", "object", "properties", Map.of());
        }

        @Override
        @SuppressWarnings("unchecked")
        public CompletableFuture<ToolResult> execute(Map input, ToolExecutionContext context) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    String result = client.callTool(mcpTool.getName(), input);
                    return ToolResult.success(result);
                } catch (Exception e) {
                    return ToolResult.error("MCP tool error: " + e.getMessage());
                }
            });
        }
    }
}
