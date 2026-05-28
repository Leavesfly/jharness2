package io.leavesfly.jharness2.engine.ext.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 HTTP + SSE 的 MCP 传输实现。
 * 通过 HTTP POST 发送请求，通过 SSE stream 接收响应。
 * 兼容 MCP 规范的 Streamable HTTP 传输方式。
 */
public class HttpSseMcpTransport implements McpTransport {

    private static final Logger logger = LoggerFactory.getLogger(HttpSseMcpTransport.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    private final String serverName;
    private final String baseUrl;
    private final Map<String, String> headers;
    private final OkHttpClient httpClient;

    private volatile boolean connected;
    private String sessionId;
    private EventSource sseEventSource;
    private Consumer<JsonNode> messageHandler;

    /**
     * @param serverName 服务器标识名称
     * @param baseUrl    MCP Server 的基础 URL（如 http://localhost:3000/mcp）
     * @param headers    自定义请求头（如认证 token）
     */
    public HttpSseMcpTransport(String serverName, String baseUrl, Map<String, String> headers) {
        this.serverName = serverName;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.headers = headers != null ? headers : Map.of();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void connect() throws IOException {
        CountDownLatch connectionLatch = new CountDownLatch(1);

        Request.Builder requestBuilder = new Request.Builder()
                .url(baseUrl)
                .header("Accept", "text/event-stream");
        headers.forEach(requestBuilder::header);

        EventSource.Factory factory = EventSources.createFactory(httpClient);
        sseEventSource = factory.newEventSource(requestBuilder.build(), new EventSourceListener() {
            @Override
            public void onOpen(EventSource eventSource, Response response) {
                String session = response.header("Mcp-Session-Id");
                if (session != null) {
                    sessionId = session;
                }
                connected = true;
                connectionLatch.countDown();
                logger.info("SSE connection opened for MCP server '{}', sessionId={}", serverName, sessionId);
            }

            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                try {
                    JsonNode msg = MAPPER.readTree(data);
                    if (messageHandler != null) {
                        messageHandler.accept(msg);
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse SSE message from '{}': {}", serverName, data);
                }
            }

            @Override
            public void onClosed(EventSource eventSource) {
                connected = false;
                logger.info("SSE connection closed for MCP server '{}'", serverName);
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, Response response) {
                connected = false;
                connectionLatch.countDown();
                logger.warn("SSE connection failed for '{}': {}", serverName,
                        t != null ? t.getMessage() : "HTTP " + (response != null ? response.code() : "unknown"));
            }
        });

        try {
            if (!connectionLatch.await(30, TimeUnit.SECONDS)) {
                throw new IOException("SSE connection timeout for MCP server '" + serverName + "'");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("SSE connection interrupted for MCP server '" + serverName + "'", e);
        }

        if (!connected) {
            throw new IOException("Failed to establish SSE connection to MCP server '" + serverName + "'");
        }
    }

    @Override
    public void send(JsonNode message) throws IOException {
        if (!connected) {
            throw new IOException("HTTP/SSE transport not connected for server '" + serverName + "'");
        }

        Request.Builder requestBuilder = new Request.Builder()
                .url(baseUrl)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        headers.forEach(requestBuilder::header);
        if (sessionId != null) {
            requestBuilder.header("Mcp-Session-Id", sessionId);
        }

        RequestBody body = RequestBody.create(message.toString(), JSON_MEDIA_TYPE);
        requestBuilder.post(body);

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("MCP HTTP request failed: HTTP " + response.code());
            }

            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                String contentType = response.header("Content-Type", "");
                if (contentType.contains("application/json")) {
                    // 同步 JSON 响应（Streamable HTTP 模式）
                    JsonNode responseMsg = MAPPER.readTree(responseBody.string());
                    if (messageHandler != null) {
                        messageHandler.accept(responseMsg);
                    }
                }
                // 如果是 text/event-stream，消息会通过 SSE 通道回调
            }
        }
    }

    @Override
    public void onMessage(Consumer<JsonNode> handler) {
        this.messageHandler = handler;
    }

    @Override
    public boolean isAlive() {
        return connected;
    }

    @Override
    public void close() {
        connected = false;
        if (sseEventSource != null) {
            sseEventSource.cancel();
        }

        // 发送 DELETE 请求终止会话（如果有 sessionId）
        if (sessionId != null) {
            try {
                Request.Builder requestBuilder = new Request.Builder()
                        .url(baseUrl)
                        .delete();
                headers.forEach(requestBuilder::header);
                requestBuilder.header("Mcp-Session-Id", sessionId);
                httpClient.newCall(requestBuilder.build()).execute().close();
            } catch (Exception e) {
                logger.debug("Failed to send session DELETE for '{}': {}", serverName, e.getMessage());
            }
        }

        httpClient.dispatcher().executorService().shutdown();
        logger.info("HTTP/SSE transport closed for MCP server '{}'", serverName);
    }
}
