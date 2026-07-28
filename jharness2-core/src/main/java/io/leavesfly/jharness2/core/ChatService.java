package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.core.checkpoint.SessionCheckpointService;
import io.leavesfly.jharness2.core.pipeline.ChatInterceptorChain;
import io.leavesfly.jharness2.core.quota.ConcurrencyLimiter;
import io.leavesfly.jharness2.core.quota.QuotaPolicy;
import io.leavesfly.jharness2.core.quota.UsageAggregator;
import io.leavesfly.jharness2.core.quota.UsageRecord;
import io.leavesfly.jharness2.core.ratelimit.RateLimitExceededException;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.stream.StreamEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    /** 请求执行期间刷新引擎缓存访问时钟的间隔，防止长任务被 expireAfterAccess 误驱逐 */
    private static final long KEEPALIVE_INTERVAL_MINUTES = 5;

    private final UserEngineRegistry engineRegistry;
    private final ChatInterceptorChain interceptorChain;
    private final UsageAggregator usageAggregator;
    private final QuotaPolicy quotaPolicy;
    private final ConcurrencyLimiter concurrencyLimiter;
    private final SessionCheckpointService checkpointService;
    /** 长任务保活调度器：仅做缓存 touch，单线程足够 */
    private final ScheduledExecutorService keepaliveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jh2-chat-keepalive");
        t.setDaemon(true);
        return t;
    });

    public ChatService(UserEngineRegistry engineRegistry,
                       @Autowired(required = false) ChatInterceptorChain interceptorChain,
                       @Autowired(required = false) UsageAggregator usageAggregator,
                       @Autowired(required = false) QuotaPolicy quotaPolicy,
                       @Autowired(required = false) ConcurrencyLimiter concurrencyLimiter,
                       @Autowired(required = false) SessionCheckpointService checkpointService) {
        this.engineRegistry = engineRegistry;
        this.interceptorChain = interceptorChain != null ? interceptorChain : new ChatInterceptorChain();
        this.usageAggregator = usageAggregator;
        this.quotaPolicy = quotaPolicy;
        this.concurrencyLimiter = concurrencyLimiter;
        this.checkpointService = checkpointService;
    }

    /**
     * 发送消息并通过类型安全的适配器将流式事件转换为 DTO 回调。
     * <p>
     * 负责三件与多用户资源治理相关的事：跟踪活跃请求数（支持优雅关闭）、
     * 占用每用户并发槽位、按本次请求的 token 增量记账（驱动 token 配额）。
     */
    public CompletableFuture<Void> chat(UserContext context, String message,
                                        Consumer<ChatEventDto> eventConsumer) {
        interceptorChain.beforeChat(context, message);
        acquireConcurrencySlot(context.getUserId());

        EngineInstance instance;
        QueryEngine engine;
        try {
            instance = engineRegistry.getOrCreate(context);
            engine = instance.getEngine();
            instance.acquireRequest();
        } catch (RuntimeException e) {
            // 引擎获取失败（超限/状态异常）时必须归还并发槽位，否则槽位永久泄漏
            releaseConcurrencySlot(context.getUserId());
            throw e;
        }

        long inputTokensBefore = engine.getCostTracker().getInputTokens();
        long outputTokensBefore = engine.getCostTracker().getOutputTokens();
        logger.debug("Submitting message for user={}, session={}",
                context.getUserId(), context.getSessionId());

        // 执行期间周期性 touch 缓存：expireAfterAccess 的时钟只在缓存访问时刷新，
        // 不 touch 的话超过空闲超时的长任务会在执行中途被驱逐并取消
        ScheduledFuture<?> keepalive = keepaliveScheduler.scheduleAtFixedRate(
                () -> engineRegistry.get(context.getUserId(), context.getSessionId()),
                KEEPALIVE_INTERVAL_MINUTES, KEEPALIVE_INTERVAL_MINUTES, TimeUnit.MINUTES);

        return engine.submitMessage(message, (StreamEvent event) -> {
            ChatEventDto dto = StreamEventAdapter.adapt(event);
            if (dto != null) {
                eventConsumer.accept(dto);
            }
        }).whenComplete((result, error) -> {
            keepalive.cancel(false);
            instance.releaseRequest();
            releaseConcurrencySlot(context.getUserId());
            recordUsage(context, engine, inputTokensBefore, outputTokensBefore);
            // 请求结束时 touch 一次缓存，刷新 expireAfterAccess 时钟，
            // 避免长请求刚结束就因"空闲"被驱逐
            engineRegistry.get(context.getUserId(), context.getSessionId());
            checkpointIfNeeded(instance, error);
            interceptorChain.afterChat(context, message, error);
        });
    }

    /**
     * 请求成功完成后推进 checkpoint 计数（按配置间隔自动快照）。
     */
    private void checkpointIfNeeded(EngineInstance instance, Throwable error) {
        if (checkpointService == null || error != null) {
            return;
        }
        try {
            checkpointService.onTurnCompleted(instance);
        } catch (Exception e) {
            logger.warn("Checkpoint after chat failed: session={}, error={}",
                    instance.getUserContext().getCacheKey(), e.getMessage());
        }
    }

    /**
     * 取消会话当前请求。
     *
     * @return true = 引擎在本节点内存中并已下发取消；false = 本地未命中
     *         （分布式模式下可能在属主节点，由调用方决定是否转发）
     */
    public boolean cancelChat(String userId, String sessionId) {
        EngineInstance instance = engineRegistry.get(userId, sessionId);
        if (instance != null) {
            instance.getEngine().cancel();
            logger.info("Cancelled chat for user={}, session={}", userId, sessionId);
            return true;
        }
        return false;
    }

    @PreDestroy
    public void shutdown() {
        keepaliveScheduler.shutdownNow();
    }

    private void acquireConcurrencySlot(String userId) {
        if (concurrencyLimiter == null || quotaPolicy == null) {
            return;
        }
        int max = quotaPolicy.getQuotaLimit(userId).getMaxConcurrentRequests();
        if (!concurrencyLimiter.tryAcquire(userId, max)) {
            throw new RateLimitExceededException(
                    "Concurrent request limit reached for user: " + userId + " (max " + max + ")",
                    userId, 5);
        }
    }

    private void releaseConcurrencySlot(String userId) {
        if (concurrencyLimiter != null) {
            concurrencyLimiter.release(userId);
        }
    }

    /**
     * 按本次请求消耗的 token 增量记账。
     * <p>
     * 使用增量而非累计值，避免会话恢复时把历史 token 重复计入当日用量。
     */
    private void recordUsage(UserContext context, QueryEngine engine,
                             long inputTokensBefore, long outputTokensBefore) {
        if (usageAggregator == null) {
            return;
        }
        try {
            long inputDelta = engine.getCostTracker().getInputTokens() - inputTokensBefore;
            long outputDelta = engine.getCostTracker().getOutputTokens() - outputTokensBefore;
            if (inputDelta <= 0 && outputDelta <= 0) {
                return;
            }
            usageAggregator.record(new UsageRecord(
                    context.getUserId(), context.getSessionId(),
                    engine.getCostTracker().getModelName(),
                    Math.max(0, inputDelta), Math.max(0, outputDelta)));
        } catch (Exception e) {
            logger.warn("Failed to record usage for user={}, session={}: {}",
                    context.getUserId(), context.getSessionId(), e.getMessage());
        }
    }
}
