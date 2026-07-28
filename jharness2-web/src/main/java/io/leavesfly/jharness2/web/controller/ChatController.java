package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.core.ChatEventDto;
import io.leavesfly.jharness2.core.ChatService;
import io.leavesfly.jharness2.core.EngineLimitException;
import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.distributed.EngineOwnedByOtherNodeException;
import io.leavesfly.jharness2.core.quota.QuotaExceededException;
import io.leavesfly.jharness2.core.ratelimit.RateLimitExceededException;
import io.leavesfly.jharness2.engine.EngineBusyException;
import io.leavesfly.jharness2.web.dto.ChatRequest;
import io.leavesfly.jharness2.web.forward.ChatForwardService;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    /** sessionId 白名单校验，防止路径穿越等恶意输入进入缓存 key / 存储层 */
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final ChatService chatService;
    private final ChatForwardService forwardService;
    private final long sseTimeoutMs;
    /** 断线后延迟取消的宽限期；0 表示立即取消（旧行为） */
    private final long disconnectGraceMs;
    /** 宽限取消调度器：仅做延迟 cancel 检查，单线程足够 */
    private final ScheduledExecutorService cancelScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jh2-sse-grace-cancel");
        t.setDaemon(true);
        return t;
    });

    public ChatController(ChatService chatService,
                         @Autowired(required = false) ChatForwardService forwardService,
                         @Value("${jharness2.chat.sse-timeout-ms:1800000}") long sseTimeoutMs,
                         @Value("${jharness2.chat.disconnect-grace-ms:300000}") long disconnectGraceMs) {
        this.chatService = chatService;
        this.forwardService = forwardService;
        this.sseTimeoutMs = sseTimeoutMs;
        this.disconnectGraceMs = disconnectGraceMs;
    }

    @PostMapping(value = "/{sessionId}/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(Authentication auth,
                                  @PathVariable String sessionId,
                                  @Valid @RequestBody ChatRequest request,
                                  HttpServletRequest httpRequest) {
        validateSessionId(sessionId);
        String username = auth.getName();
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);

        // workspace 传 null：统一由 WorkspaceInitializer(WorkspaceStorage SPI) 分配，避免绕过存储抽象
        UserContext context = new UserContext(
                username, sessionId, null,
                request.getModel(), null, null);

        // 持有本次请求的 future：宽限取消时用它判定身份，避免误杀重连后的新请求
        AtomicReference<CompletableFuture<Void>> futureRef = new AtomicReference<>();
        try {
            CompletableFuture<Void> chatFuture = chatService.chat(context, request.getMessage(), (ChatEventDto dto) -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name(dto.getType())
                            .data(dto, MediaType.APPLICATION_JSON));
                } catch (IOException e) {
                    // 客户端断连：不立即杀死 Agent 循环，给它一个宽限期跑完并落库，
                    // 用户刷新页面后可从会话历史看到结果；宽限期后仍未完成才取消
                    logger.warn("SSE send failed (client disconnected), scheduling grace cancel for user={}, session={}",
                            username, sessionId);
                    scheduleCancelAfterGrace(username, sessionId, futureRef.get());
                    emitter.completeWithError(e);
                }
            });
            futureRef.set(chatFuture);
            chatFuture.whenComplete((v, ex) -> {
                try {
                    if (ex != null) {
                        Throwable cause = ex instanceof CompletionException && ex.getCause() != null
                                ? ex.getCause() : ex;
                        String message = cause instanceof EngineBusyException
                                ? "当前会话正在处理另一条消息，请等待完成后重试"
                                : cause.getMessage();
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(ChatEventDto.error(message), MediaType.APPLICATION_JSON));
                    }
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            });
        } catch (RateLimitExceededException | QuotaExceededException | EngineLimitException e) {
            // 限流/配额/引擎数超限是同步抛出的，在 SSE 通道内以 error 事件告知客户端
            logger.info("Chat rejected for user={}, session={}: {}", username, sessionId, e.getMessage());
            completeWithErrorEvent(emitter, e.getMessage());
            return emitter;
        } catch (EngineOwnedByOtherNodeException e) {
            // 分布式模式：优先尝试内置反向代理转发到属主节点（SSE 透传），
            // 已转发过的请求不再二次转发（防属主易主瞬间的 A→B→A 环路）
            boolean alreadyForwarded = httpRequest.getHeader(ChatForwardService.FORWARDED_HEADER) != null;
            if (!alreadyForwarded && forwardService != null && forwardService.isEnabled()
                    && forwardService.forwardChat(e.getOwnerNodeId(), sessionId,
                            httpRequest.getHeader("Authorization"), request, emitter)) {
                logger.info("Chat forwarded to owner node for user={}, session={}, owner={}",
                        username, sessionId, e.getOwnerNodeId());
                return emitter;
            }
            // 转发不可用（未配置地址/属主已下线/防环）：明确告知重试而不是抛 500
            logger.warn("Chat hit non-owner node (forward unavailable) for user={}, session={}: {}",
                    username, sessionId, e.getMessage());
            completeWithErrorEvent(emitter, "会话正由另一节点处理，请稍后重试");
            return emitter;
        }
        emitter.onTimeout(() -> {
            logger.warn("SSE timeout for user={}, session={}, scheduling grace cancel", username, sessionId);
            scheduleCancelAfterGrace(username, sessionId, futureRef.get());
        });
        // 异步通道异常（如客户端断连）：宽限期后仍未完成才取消 Agent 任务
        emitter.onError(t -> {
            logger.warn("SSE error for user={}, session={}: {}", username, sessionId, t.getMessage());
            scheduleCancelAfterGrace(username, sessionId, futureRef.get());
        });

        return emitter;
    }

    /**
     * 断线宽限取消：宽限期内任务跑完则正常落库（用户刷新可见结果），
     * 到期仍未完成才取消，避免无限消耗 LLM token。
     * 用 future 身份判定：若用户重连后发起了新请求，旧 future 已完成，不会误杀新请求。
     */
    private void scheduleCancelAfterGrace(String username, String sessionId, CompletableFuture<Void> chatFuture) {
        if (chatFuture == null || chatFuture.isDone()) {
            return;
        }
        if (disconnectGraceMs <= 0) {
            safeCancel(username, sessionId);
            return;
        }
        cancelScheduler.schedule(() -> {
            if (!chatFuture.isDone()) {
                logger.info("Disconnect grace period expired, cancelling chat for user={}, session={}",
                        username, sessionId);
                safeCancel(username, sessionId);
            }
        }, disconnectGraceMs, TimeUnit.MILLISECONDS);
    }

    private void safeCancel(String username, String sessionId) {
        try {
            chatService.cancelChat(username, sessionId);
        } catch (Exception e) {
            // 引擎可能已在关闭/驱逐中，取消失败不影响主流程
            logger.debug("Cancel chat failed (engine may be closing): user={}, session={}, error={}",
                    username, sessionId, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        cancelScheduler.shutdownNow();
    }

    /**
     * 将同步抛出的拒绝类异常（限流、配额、引擎数超限）以 SSE error 事件下发。
     * <p>
     * 该接口声明的是 text/event-stream，若让异常冒泡到全局处理器，客户端会收到
     * 不符合流协议的 JSON 应答，导致前端流解析失败。
     */
    private void completeWithErrorEvent(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(ChatEventDto.error(message), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<?> cancelChat(Authentication auth, @PathVariable String sessionId,
                                        HttpServletRequest httpRequest) {
        validateSessionId(sessionId);
        boolean cancelledLocally = chatService.cancelChat(auth.getName(), sessionId);
        // 本地未命中且未转发过：分布式模式下引擎可能在属主节点，把取消送过去
        if (!cancelledLocally && forwardService != null && forwardService.isEnabled()
                && httpRequest.getHeader(ChatForwardService.FORWARDED_HEADER) == null) {
            forwardService.forwardCancel(auth.getName(), sessionId,
                    httpRequest.getHeader("Authorization"));
        }
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    @PostMapping("/new")
    public ResponseEntity<?> newSession(Authentication auth) {
        String sessionId = UUID.randomUUID().toString();
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    private static void validateSessionId(String sessionId) {
        if (sessionId == null || !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sessionId");
        }
    }
}
