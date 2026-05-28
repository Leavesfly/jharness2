package io.leavesfly.jharness2.engine.ext.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 Stdio 的 MCP 传输实现。
 * 启动本地子进程，通过 stdin/stdout 交换 JSON-RPC 消息。
 */
public class StdioMcpTransport implements McpTransport {

    private static final Logger logger = LoggerFactory.getLogger(StdioMcpTransport.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String serverName;
    private final List<String> command;
    private final Map<String, String> env;

    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private volatile boolean running;
    private Thread readerThread;
    private Consumer<JsonNode> messageHandler;

    public StdioMcpTransport(String serverName, List<String> command, Map<String, String> env) {
        this.serverName = serverName;
        this.command = command;
        this.env = env != null ? env : Map.of();
    }

    @Override
    public void connect() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        pb.redirectErrorStream(false);
        process = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        running = true;

        readerThread = new Thread(this::readLoop, "mcp-stdio-reader-" + serverName);
        readerThread.setDaemon(true);
        readerThread.start();

        logger.info("Stdio transport connected for MCP server '{}'", serverName);
    }

    @Override
    public void send(JsonNode message) throws IOException {
        if (!running) {
            throw new IOException("Stdio transport not running for server '" + serverName + "'");
        }
        synchronized (writer) {
            writer.write(message.toString());
            writer.newLine();
            writer.flush();
        }
    }

    @Override
    public void onMessage(Consumer<JsonNode> handler) {
        this.messageHandler = handler;
    }

    @Override
    public boolean isAlive() {
        return running && process != null && process.isAlive();
    }

    @Override
    public void close() {
        running = false;
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
        logger.info("Stdio transport closed for MCP server '{}'", serverName);
    }

    private void readLoop() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                try {
                    JsonNode msg = MAPPER.readTree(line);
                    if (messageHandler != null) {
                        messageHandler.accept(msg);
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse MCP message from '{}': {}", serverName, line);
                }
            }
        } catch (IOException e) {
            if (running) {
                logger.warn("Stdio reader loop ended for '{}': {}", serverName, e.getMessage());
            }
        }
    }
}
