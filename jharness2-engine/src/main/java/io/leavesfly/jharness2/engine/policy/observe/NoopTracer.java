package io.leavesfly.jharness2.engine.policy.observe;

import java.util.List;
import java.util.Map;

/**
 * 无操作 Tracer — 零开销实现，用于不需要追踪的场景。
 */
public class NoopTracer implements Tracer {

    public static final NoopTracer INSTANCE = new NoopTracer();
    private static final Span NOOP_SPAN = new NoopSpan();

    @Override
    public Span startSpan(String name) { return NOOP_SPAN; }

    @Override
    public Span startSpan(String name, Map<String, Object> attributes) { return NOOP_SPAN; }

    @Override
    public Span currentSpan() { return NOOP_SPAN; }

    @Override
    public List<SpanData> exportSpans() { return List.of(); }

    @Override
    public void clear() {}

    private static class NoopSpan implements Span {
        @Override public String getSpanId() { return "noop"; }
        @Override public String getName() { return "noop"; }
        @Override public void setAttribute(String key, Object value) {}
        @Override public void setAttributes(Map<String, Object> attributes) {}
        @Override public void setStatus(SpanStatus status) {}
        @Override public void setError(Throwable throwable) {}
        @Override public Span startChild(String name) { return this; }
        @Override public Span startChild(String name, Map<String, Object> attributes) { return this; }
        @Override public long getStartTimeMs() { return 0; }
        @Override public long getDurationMs() { return 0; }
        @Override public boolean isEnded() { return true; }
        @Override public void close() {}
    }
}
