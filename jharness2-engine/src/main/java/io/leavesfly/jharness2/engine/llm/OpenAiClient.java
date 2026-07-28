package io.leavesfly.jharness2.engine.llm;

import io.leavesfly.jharness2.engine.message.ConversationMessage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.leavesfly.jharness2.engine.stream.AssistantTextDelta;
import io.leavesfly.jharness2.engine.stream.StreamEvent;
import io.leavesfly.jharness2.engine.stream.UsageReport;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class OpenAiClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiClient.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 按超时参数共享 OkHttpClient。
     * <p>
     * 多用户服务下每个会话会创建一个引擎，若每个引擎都新建 OkHttpClient，
     * 上百个并发会话就会堆出上百个连接池与调度线程池（单机版遗留写法）。
     * 共享实例后连接复用、线程数与会话数解耦。
     */
    private static final Map<String, OkHttpClient> SHARED_CLIENTS = new ConcurrentHashMap<>();

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;
    private final OkHttpClient httpClient;

    public OpenAiClient(String baseUrl, String apiKey, String model, int maxTokens,
                        int connectTimeoutSeconds, int readTimeoutSeconds, int writeTimeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.httpClient = sharedClient(connectTimeoutSeconds, readTimeoutSeconds, writeTimeoutSeconds);
    }

    private static OkHttpClient sharedClient(int connectTimeoutSeconds, int readTimeoutSeconds,
                                             int writeTimeoutSeconds) {
        String key = connectTimeoutSeconds + ":" + readTimeoutSeconds + ":" + writeTimeoutSeconds;
        return SHARED_CLIENTS.computeIfAbsent(key, k -> new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(writeTimeoutSeconds, TimeUnit.SECONDS)
                .build());
    }

    @Override
    public LlmResponse chatStream(List<ConversationMessage> messages, List<Map<String, Object>> tools,
                                   Consumer<StreamEvent> eventConsumer) {
        ObjectNode requestBody = buildRequestBody(messages, tools);
        String url = baseUrl + "/chat/completions";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(requestBody.toString(), JSON_TYPE))
                .build();

        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder contentBuilder = new StringBuilder();
        List<ConversationMessage.ToolCall> toolCallsResult = new ArrayList<>();
        Map<Integer, StringBuilder> toolCallArgs = new HashMap<>();
        Map<Integer, String> toolCallIds = new HashMap<>();
        Map<Integer, String> toolCallNames = new HashMap<>();
        long[] tokenCounts = {0, 0}; // [promptTokens, completionTokens]

        EventSource.Factory factory = EventSources.createFactory(httpClient);
        factory.newEventSource(request, new EventSourceListener() {

            @Override
            public void onEvent(EventSource es, String id, String type, String data) {
                if ("[DONE]".equals(data)) {
                    assembleToolCalls(toolCallIds, toolCallNames, toolCallArgs, toolCallsResult);
                    latch.countDown();
                    return;
                }
                try {
                    JsonNode json = MAPPER.readTree(data);
                    JsonNode choices = json.path("choices");
                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode delta = choices.get(0).path("delta");
                        String content = delta.path("content").asText(null);
                        if (content != null && !content.isEmpty()) {
                            contentBuilder.append(content);
                            eventConsumer.accept(new AssistantTextDelta(content));
                        }
                        JsonNode tcArray = delta.path("tool_calls");
                        if (tcArray.isArray()) {
                            for (JsonNode tc : tcArray) {
                                int idx = tc.path("index").asInt(0);
                                String tcId = tc.path("id").asText(null);
                                if (tcId != null) toolCallIds.put(idx, tcId);
                                JsonNode fn = tc.path("function");
                                String fnName = fn.path("name").asText(null);
                                if (fnName != null) toolCallNames.put(idx, fnName);
                                String fnArgs = fn.path("arguments").asText("");
                                toolCallArgs.computeIfAbsent(idx, k -> new StringBuilder()).append(fnArgs);
                            }
                        }
                    }
                    JsonNode usage = json.path("usage");
                    if (!usage.isMissingNode()) {
                        tokenCounts[0] = usage.path("prompt_tokens").asLong(0);
                        tokenCounts[1] = usage.path("completion_tokens").asLong(0);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse SSE data: {}", data, e);
                }
            }

            @Override
            public void onFailure(EventSource es, Throwable t, Response response) {
                logger.error("SSE stream failed: {}", t != null ? t.getMessage() : "unknown", t);
                latch.countDown();
            }

            @Override
            public void onClosed(EventSource es) {
                assembleToolCalls(toolCallIds, toolCallNames, toolCallArgs, toolCallsResult);
                latch.countDown();
            }
        });

        try {
            latch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (tokenCounts[0] > 0 || tokenCounts[1] > 0) {
            eventConsumer.accept(new UsageReport(tokenCounts[0], tokenCounts[1],
                    tokenCounts[0] + tokenCounts[1], 0));
        }

        return new LlmResponse(contentBuilder.toString(), toolCallsResult, tokenCounts[0], tokenCounts[1]);
    }

    private void assembleToolCalls(Map<Integer, String> ids, Map<Integer, String> names,
                                   Map<Integer, StringBuilder> args,
                                   List<ConversationMessage.ToolCall> result) {
        if (result.isEmpty() && !ids.isEmpty()) {
            ids.keySet().stream().sorted().forEach(idx -> {
                String tcId = ids.getOrDefault(idx, "call_" + idx);
                String fnName = names.getOrDefault(idx, "unknown");
                String fnArgs = args.containsKey(idx) ? args.get(idx).toString() : "{}";
                result.add(new ConversationMessage.ToolCall(tcId, "function",
                        new ConversationMessage.FunctionCall(fnName, fnArgs)));
            });
        }
    }

    private ObjectNode buildRequestBody(List<ConversationMessage> messages, List<Map<String, Object>> tools) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("stream", true);

        ArrayNode messagesArray = body.putArray("messages");
        for (ConversationMessage msg : messages) {
            ObjectNode msgNode = MAPPER.createObjectNode();
            msgNode.put("role", msg.getRole().name().toLowerCase());
            if (msg.getContent() != null) msgNode.put("content", msg.getContent());
            if (msg.getToolCallId() != null) msgNode.put("tool_call_id", msg.getToolCallId());
            if (msg.getName() != null) msgNode.put("name", msg.getName());
            if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                ArrayNode tcArr = msgNode.putArray("tool_calls");
                for (ConversationMessage.ToolCall tc : msg.getToolCalls()) {
                    ObjectNode tcNode = MAPPER.createObjectNode();
                    tcNode.put("id", tc.getId());
                    tcNode.put("type", "function");
                    ObjectNode fnNode = tcNode.putObject("function");
                    fnNode.put("name", tc.getFunction().getName());
                    fnNode.put("arguments", tc.getFunction().getArguments());
                    tcArr.add(tcNode);
                }
            }
            messagesArray.add(msgNode);
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (Map<String, Object> tool : tools) {
                toolsArray.add(MAPPER.valueToTree(tool));
            }
        }

        return body;
    }

    /**
     * 引擎关闭时不能销毁共享的 HTTP 客户端：它还在服务其他用户的会话。
     * 连接池由 OkHttp 自行按空闲时长回收。
     */
    @Override
    public void close() {
        // no-op：HTTP 客户端为进程级共享资源
    }
}
