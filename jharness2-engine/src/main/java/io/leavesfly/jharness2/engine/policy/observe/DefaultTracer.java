package io.leavesfly.jharness2.engine.policy.observe;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认 Tracer 实现 — 内存存储所有 Span，适用于调试、测试和评估。
 * <p>
 * 通过 {@link #exportSpans()} 可导出完整的追踪树，支持序列化为 JSON 后回放分析。
 */
public class DefaultTracer implements Tracer {

    private final List<DefaultSpan> rootSpans = new CopyOnWriteArrayList<>();
    private volatile DefaultSpan currentSpan;

    @Override
    public Span startSpan(String name) {
        return startSpan(name, null);
    }

    @Override
    public Span startSpan(String name, Map<String, Object> attributes) {
        DefaultSpan span = new DefaultSpan(name, null);
        if (attributes != null) {
            span.setAttributes(attributes);
        }
        rootSpans.add(span);
        currentSpan = span;
        return span;
    }

    @Override
    public Span currentSpan() {
        return currentSpan;
    }

    @Override
    public List<SpanData> exportSpans() {
        return rootSpans.stream()
                .map(DefaultSpan::toSpanData)
                .toList();
    }

    @Override
    public void clear() {
        rootSpans.clear();
        currentSpan = null;
    }
}
