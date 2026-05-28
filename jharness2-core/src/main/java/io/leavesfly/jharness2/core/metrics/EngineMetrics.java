package io.leavesfly.jharness2.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.leavesfly.jharness2.core.UserEngineRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JHarness2 Core 层 Metrics 埋点 —— 基于 Micrometer。
 * <p>
 * 暴露的核心指标：
 * <ul>
 *   <li>jharness2.engine.active - 当前活跃引擎数（Gauge）</li>
 *   <li>jharness2.engine.created.total - 引擎创建总数（Counter）</li>
 *   <li>jharness2.engine.evicted.total - 引擎驱逐总数（Counter）</li>
 *   <li>jharness2.engine.create.duration - 引擎创建耗时（Timer）</li>
 *   <li>jharness2.chat.requests.total - 聊天请求总数（Counter）</li>
 *   <li>jharness2.chat.errors.total - 聊天错误总数（Counter）</li>
 *   <li>jharness2.chat.duration - 聊天请求耗时（Timer）</li>
 *   <li>jharness2.engine.limit.exceeded.total - 引擎限额超限次数（Counter）</li>
 *   <li>jharness2.checkpoint.saved.total - Checkpoint 保存次数（Counter）</li>
 * </ul>
 */
public class EngineMetrics {

    private static final Logger logger = LoggerFactory.getLogger(EngineMetrics.class);
    private static final String PREFIX = "jharness2";

    private final Counter engineCreatedCounter;
    private final Counter engineEvictedCounter;
    private final Timer engineCreateTimer;
    private final Counter chatRequestsCounter;
    private final Counter chatErrorsCounter;
    private final Timer chatDurationTimer;
    private final Counter engineLimitExceededCounter;
    private final Counter checkpointSavedCounter;
    private final AtomicLong activeEngineGauge = new AtomicLong(0);

    public EngineMetrics(MeterRegistry registry, UserEngineRegistry engineRegistry) {
        // Gauge: 活跃引擎数（直接查询 registry）
        Gauge.builder(PREFIX + ".engine.active", engineRegistry, reg -> reg.activeEngineCount())
                .description("Number of currently active engine instances")
                .register(registry);

        this.engineCreatedCounter = Counter.builder(PREFIX + ".engine.created.total")
                .description("Total number of engines created")
                .register(registry);

        this.engineEvictedCounter = Counter.builder(PREFIX + ".engine.evicted.total")
                .description("Total number of engines evicted")
                .register(registry);

        this.engineCreateTimer = Timer.builder(PREFIX + ".engine.create.duration")
                .description("Duration of engine creation")
                .publishPercentiles(0.5, 0.95, 0.99)
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

        this.engineLimitExceededCounter = Counter.builder(PREFIX + ".engine.limit.exceeded.total")
                .description("Total number of engine limit exceeded events")
                .register(registry);

        this.checkpointSavedCounter = Counter.builder(PREFIX + ".checkpoint.saved.total")
                .description("Total number of checkpoints saved")
                .register(registry);

        logger.info("EngineMetrics registered with MeterRegistry");
    }

    public void recordEngineCreated() {
        engineCreatedCounter.increment();
    }

    public void recordEngineEvicted() {
        engineEvictedCounter.increment();
    }

    public Timer.Sample startEngineCreateTimer() {
        return Timer.start();
    }

    public void stopEngineCreateTimer(Timer.Sample sample) {
        sample.stop(engineCreateTimer);
    }

    public void recordChatRequest() {
        chatRequestsCounter.increment();
    }

    public void recordChatError() {
        chatErrorsCounter.increment();
    }

    public Timer.Sample startChatTimer() {
        return Timer.start();
    }

    public void stopChatTimer(Timer.Sample sample) {
        sample.stop(chatDurationTimer);
    }

    public void recordEngineLimitExceeded() {
        engineLimitExceededCounter.increment();
    }

    public void recordCheckpointSaved() {
        checkpointSavedCounter.increment();
    }
}
