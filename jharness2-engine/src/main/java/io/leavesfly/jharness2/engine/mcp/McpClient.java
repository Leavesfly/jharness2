package io.leavesfly.jharness2.engine.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class McpClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String serverName;
    private final List<String> command;
    private final Map<String, String> env;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private final AtomicInteger requestId = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private volatile boolean running;
    private Thread readerThread;

    public McpClient(String serverName, List<String> command, Map<String, String> env) {
        this.serverName = serverName;
        this.command = command;
        this.env = env != null ? env : Map.of();
    }

    public void connect() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        pb.redirectErrorStream(false);
        process = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        running = true;

        readerThread = new Thread(this::readLoop, "mcp-reader-" + serverName);
        readerThread.setDaemon(true);
        readerThread.start();

        // Initialize
        JsonNode initResult = sendRequest("initialize", MAPPER.createObjectNode()
                .put("protocolVersion", "2024-11-05")
                .set("capabilities", MAPPER.createObjectNode()));
        logger.info("MCP server '{}' initialized: {}", serverName,
                initResult != null ? initResult.path("serverInfo").path("name").asText("unknown") : "unknown");

        sendNotification("notifications/initialized", MAPPER.createObjectNode());
    }

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
            if (content.isArray() && content.size() > 0) {
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
            synchronized (writer) {
                writer.write(request.toString());
                writer.newLine();
                writer.flush();
            }
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            pendingRequests.remove(id);
            logger.warn("MCP request '{}' failed: {}", method, e.getMessage());
            return null;
        }
    }

    private void sendNotification(String method, JsonNode params) {
        ObjectNode notification = MAPPER.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", params);
        try {
            synchronized (writer) {
                writer.write(notification.toString());
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            logger.warn("Failed to send notification '{}'", method, e);
        }
    }

    private void readLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                try {
                    JsonNode msg = MAPPER.readTree(line);
                    if (msg.has("id") && msg.has("result")) {
                        int id = msg.get("id").asInt();
                        CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                        if (future != null) {
                            future.complete(msg.get("result"));
                        }
                    } else if (msg.has("id") && msg.has("error")) {
                        int id = msg.get("id").asInt();
                        CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                        if (future != null) {
                            future.completeExceptionally(new RuntimeException(
                                    msg.get("error").path("message").asText("Unknown error")));
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse MCP message: {}", line);
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.warn("MCP reader loop ended: {}", e.getMessage());
            }
        }
    }

    public String getServerName() { return serverName; }

    public boolean isRunning() { return running && process != null && process.isAlive(); }

    @Override
    public void close() {
        running = false;
        pendingRequests.values().forEach(f -> f.cancel(true));
        pendingRequests.clear();
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("MCP client '{}' closed", serverName);
    }
}
