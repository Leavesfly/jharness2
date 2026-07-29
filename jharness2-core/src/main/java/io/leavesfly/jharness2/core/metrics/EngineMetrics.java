package io.leavesfly.jharness2.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.leavesfly.jharness2.core.UserEngineRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * JHarness2 Core 层 Metrics 埋点 —— 基于 Micrometer。
 * <p>
 * 暴露的核心指标：
 * <ul>
 *   <li>jharness2.engine.active - 当前活跃引擎数（Gauge）</li>
 *   <li>jharness2.chat.requests.total - 聊天请求总数（Counter）</li>
 *   <li>jharness2.chat.errors.total - 聊天错误总数（Counter）</li>
 *   <li>jharness2.chat.duration - 聊天请求耗时（Timer）</li>
 * </ul>
 */
public class EngineMetrics {

    private static final Logger logger = LoggerFactory.getLogger(EngineMetrics.class);
    private static final String PREFIX = "jharness2";

    private final Counter chatRequestsCounter;
    private final Counter chatErrorsCounter;
    private final Timer chatDurationTimer;

    public EngineMetrics(MeterRegistry registry, UserEngineRegistry engineRegistry) {
        // Gauge: 活跃引擎数（直接查询 registry）
        Gauge.builder(PREFIX + ".engine.active", engineRegistry, reg -> reg.activeEngineCount())
                .description("Number of currently active engine instances")
                .register(registry);

        this.chatRequestsCounter = Counter.builder(PREFIX + ".chat.requests.total")
                .description("Total number of chat requests")
                .register(registry);

        this.chatErrorsCounter = Counter.builder(PREFIX + ".chat.errors.total")
                .description("Total number of chat request errors")
                .register(registry);

        this.chatDurationTimer = Timer.builder(PREFIX + ".chat.duration")
                .description("Duration of chat requests")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        logger.info("EngineMetrics registered with MeterRegistry");
    }

    public void recordChatRequest() {
        chatRequestsCounter.increment();
    }

    public void recordChatError() {
        chatErrorsCounter.increment();
    }

    public void recordChatDuration(long durationNanos) {
        chatDurationTimer.record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
