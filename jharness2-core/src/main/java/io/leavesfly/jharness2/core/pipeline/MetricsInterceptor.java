package io.leavesfly.jharness2.core.pipeline;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.metrics.EngineMetrics;
import io.micrometer.core.instrument.Timer;

public class MetricsInterceptor implements ChatInterceptor {
    private final EngineMetrics metrics;
    private static final ThreadLocal<Timer.Sample> timerHolder = new ThreadLocal<>();

    public MetricsInterceptor(EngineMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void beforeChat(UserContext context, String message) {
        metrics.recordChatRequest();
        timerHolder.set(metrics.startChatTimer());
    }

    @Override
    public void afterChat(UserContext context, String message, Throwable error) {
        Timer.Sample sample = timerHolder.get();
        if (sample != null) {
            metrics.stopChatTimer(sample);
            timerHolder.remove();
        }
        if (error != null) {
            metrics.recordChatError();
        }
    }

    @Override
    public int getOrder() { return 10; }
}
