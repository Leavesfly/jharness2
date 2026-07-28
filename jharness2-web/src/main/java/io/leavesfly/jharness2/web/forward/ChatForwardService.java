package io.leavesfly.jharness2.web.forward;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.leavesfly.jharness2.core.UserEngineRegistry;
import io.leavesfly.jharness2.core.distributed.DistributedEngineRegistry;
import io.leavesfly.jharness2.core.distributed.EngineStateStore;
import io.leavesfly.jharness2.core.distributed.NodeAddressRegistry;
import io.leavesfly.jharness2.web.dto.ChatRequest;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * 节点间请求转发（内置反向代理）—— 分布式模式下会话属主在其他节点时，
 * 将聊天/取消请求代理到属主节点，SSE 流式透传回客户端。
 * <p>
 * 前置条件：各节点配置 jharness2.engine.distributed.advertise-address 并共享同一 JWT 密钥
 * （原样透传 Authorization 头，属主节点独立完成认证）。
 * 防环：转发请求携带 {@link #FORWARDED_HEADER}，已转发过的请求不再二次转发。
 */
@Service
public class ChatForwardService {

    /** 防转发环路标记头：属主易主的瞬间可能出现 A→B→A，最多转发一跳 */
    public static final String FORWARDED_HEADER = "X-JH2-Forwarded";

    private static final Logger logger = LoggerFactory.getLogger(ChatForwardService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final NodeAddressRegistry nodeAddressRegistry;
    private final EngineStateStore engineStateStore;
    private final ObjectMapper objectMapper;
    /** 本节点 nodeId（仅分布式注册表下有值），用于避免把请求转发给自己 */
    private final String selfNodeId;
    private final OkHttpClient client;

    public ChatForwardService(@Autowired(required = false) NodeAddressRegistry nodeAddressRegistry,
                              @Autowired(required = false) EngineStateStore engineStateStore,
                              UserEngineRegistry engineRegistry,
                              ObjectMapper objectMapper) {
        this.nodeAddressRegistry = nodeAddressRegistry;
        this.engineStateStore = engineStateStore;
        this.objectMapper = objectMapper;
        this.selfNodeId = engineRegistry instanceof DistributedEngineRegistry distributed
                ? distributed.getNodeId() : null;
        // SSE 长连接：读超时置 0（依赖属主节点的 sse-timeout 结束流），连接失败快速暴露
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .writeTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ZERO)
                .build();
    }

    /** 是否具备转发能力（分布式模式 + 地址注册表可用） */
    public boolean isEnabled() {
        return nodeAddressRegistry != null;
    }

    /**
     * 把聊天请求转发到属主节点并将 SSE 流透传给 emitter。
     *
     * @return true = 已接管该请求（后续事件异步写入 emitter）；
     *         false = 无法转发（未启用/属主未知/地址查不到/属主是自己），调用方走兜底逻辑
     */
    public boolean forwardChat(String ownerNodeId, String sessionId, String authorization,
                               ChatRequest request, SseEmitter emitter) {
        Optional<String> address = resolveAddress(ownerNodeId);
        if (address.isEmpty()) {
            return false;
        }
        String url = address.get() + "/api/chat/" + sessionId + "/message";

        Request httpRequest;
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("message", request.getMessage());
            if (request.getModel() != null) {
                body.put("model", request.getModel());
            }
            httpRequest = baseRequest(url, authorization)
                    .header("Accept", "text/event-stream")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON))
                    .build();
        } catch (Exception e) {
            logger.warn("Failed to build forward request: session={}, error={}", sessionId, e.getMessage());
            return false;
        }

        logger.info("Forwarding chat to owner node: session={}, owner={}, url={}", sessionId, ownerNodeId, url);
        // 异步执行：SSE 流读取在 OkHttp dispatcher 线程上进行，不占用 Servlet 线程
        client.newCall(httpRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                logger.warn("Forward to owner node failed: session={}, owner={}, error={}",
                        sessionId, ownerNodeId, e.getMessage());
                completeWithError(emitter, "转发到会话属主节点失败，请稍后重试");
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        logger.warn("Owner node returned non-2xx for forwarded chat: session={}, status={}",
                                sessionId, r.code());
                        completeWithError(emitter, "会话属主节点返回错误（HTTP " + r.code() + "），请稍后重试");
                        return;
                    }
                    BufferedReader reader = new BufferedReader(r.body().charStream());
                    SseStreamRelay.relay(reader, (eventName, data) ->
                            emitter.send(SseEmitter.event()
                                    .name(eventName)
                                    .data(data, org.springframework.http.MediaType.APPLICATION_JSON)));
                    emitter.complete();
                } catch (IOException e) {
                    // 下游客户端断开或上游流中断：结束本侧 emitter，属主节点自身有断线宽限逻辑
                    logger.debug("Forwarded SSE relay terminated: session={}, error={}", sessionId, e.getMessage());
                    emitter.completeWithError(e);
                }
            }
        });
        return true;
    }

    /**
     * 把取消请求转发到属主节点（本地未命中引擎时调用）。
     *
     * @return true = 已成功转发给属主节点
     */
    public boolean forwardCancel(String userId, String sessionId, String authorization) {
        if (!isEnabled() || engineStateStore == null) {
            return false;
        }
        String ownerNodeId = engineStateStore.load(userId + ":" + sessionId)
                .map(state -> state.getOwnerNodeId()).orElse(null);
        Optional<String> address = resolveAddress(ownerNodeId);
        if (address.isEmpty()) {
            return false;
        }
        String url = address.get() + "/api/chat/" + sessionId + "/cancel";
        Request httpRequest = baseRequest(url, authorization)
                .post(RequestBody.create(new byte[0], null))
                .build();
        try (Response response = client.newCall(httpRequest).execute()) {
            logger.info("Forwarded cancel to owner node: session={}, owner={}, status={}",
                    sessionId, ownerNodeId, response.code());
            return response.isSuccessful();
        } catch (IOException e) {
            logger.warn("Forward cancel failed: session={}, owner={}, error={}",
                    sessionId, ownerNodeId, e.getMessage());
            return false;
        }
    }

    /**
     * 解析属主节点地址；属主未知、是自己、或地址未注册（节点已下线）时返回 empty。
     */
    private Optional<String> resolveAddress(String ownerNodeId) {
        if (!isEnabled() || ownerNodeId == null || ownerNodeId.isBlank()) {
            return Optional.empty();
        }
        if (ownerNodeId.equals(selfNodeId)) {
            return Optional.empty();
        }
        try {
            return nodeAddressRegistry.lookup(ownerNodeId)
                    .map(addr -> addr.endsWith("/") ? addr.substring(0, addr.length() - 1) : addr);
        } catch (Exception e) {
            logger.warn("Node address lookup failed: nodeId={}, error={}", ownerNodeId, e.getMessage());
            return Optional.empty();
        }
    }

    private Request.Builder baseRequest(String url, String authorization) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header(FORWARDED_HEADER, "1");
        if (authorization != null && !authorization.isBlank()) {
            builder.header("Authorization", authorization);
        }
        return builder;
    }

    private void completeWithError(SseEmitter emitter, String message) {
        try {
            // 与 ChatEventDto.error() 的字段格式保持一致，前端无需区分本地/转发错误
            ObjectNode error = objectMapper.createObjectNode();
            error.put("type", "error");
            error.put("error", message);
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(objectMapper.writeValueAsString(error),
                            org.springframework.http.MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
}
