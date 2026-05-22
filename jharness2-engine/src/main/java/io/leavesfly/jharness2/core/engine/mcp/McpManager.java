package io.leavesfly.jharness2.core.engine.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jharness2.core.engine.tool.BaseTool;
import io.leavesfly.jharness2.core.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.core.engine.tool.ToolRegistry;
import io.leavesfly.jharness2.core.engine.tool.ToolResult;
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
     * 将所有已连接的 MCP 服务器的工具注册到 ToolRegistry（使用 BaseTool 适配器）。
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
