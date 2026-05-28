package io.leavesfly.jharness2.engine.policy.observe;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Span 默认实现 — 内存存储，支持树形嵌套。
 */
public class DefaultSpan implements Span {

    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final long startTimeMs;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private final List<DefaultSpan> children = new CopyOnWriteArrayList<>();
    private final AtomicBoolean ended = new AtomicBoolean(false);

    private volatile long endTimeMs;
    private volatile SpanStatus status = SpanStatus.UNSET;
    private volatile String errorMessage;

    public DefaultSpan(String name, String parentSpanId) {
        this.spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.startTimeMs = System.currentTimeMillis();
    }

    @Override
    public String getSpanId() { return spanId; }

    @Override
    public String getName() { return name; }

    @Override
    public void setAttribute(String key, Object value) {
        if (!ended.get()) {
            synchronized (attributes) {
                attributes.put(key, value);
            }
        }
    }

    @Override
    public void setAttributes(Map<String, Object> attrs) {
        if (!ended.get() && attrs != null) {
            synchronized (attributes) {
                attributes.putAll(attrs);
            }
        }
    }

    @Override
    public void setStatus(SpanStatus status) {
        if (!ended.get()) {
            this.status = status;
        }
    }

    @Override
    public void setError(Throwable throwable) {
        if (!ended.get()) {
            this.status = SpanStatus.ERROR;
            this.errorMessage = throwable != null ? throwable.getMessage() : "unknown error";
        }
    }

    @Override
    public Span startChild(String name) {
        return startChild(name, null);
    }

    @Override
    public Span startChild(String name, Map<String, Object> attrs) {
        DefaultSpan child = new DefaultSpan(name, this.spanId);
        if (attrs != null) {
            child.setAttributes(attrs);
        }
        children.add(child);
        return child;
    }

    @Override
    public long getStartTimeMs() { return startTimeMs; }

    @Override
    public long getDurationMs() {
        if (ended.get()) {
            return endTimeMs - startTimeMs;
        }
        return System.currentTimeMillis() - startTimeMs;
    }

    @Override
    public boolean isEnded() { return ended.get(); }

    @Override
    public void close() {
        if (ended.compareAndSet(false, true)) {
            this.endTimeMs = System.currentTimeMillis();
            if (status == SpanStatus.UNSET) {
                status = SpanStatus.OK;
            }
        }
    }

    /**
     * 导出为不可变快照。
     */
    public SpanData toSpanData() {
        List<SpanData> childData = children.stream()
                .map(DefaultSpan::toSpanData)
                .toList();

        Map<String, Object> attrsCopy;
        synchronized (attributes) {
            attrsCopy = new LinkedHashMap<>(attributes);
        }

        return new SpanData(
                spanId, parentSpanId, name,
                startTimeMs, ended.get() ? endTimeMs : System.currentTimeMillis(),
                status, attrsCopy, errorMessage, childData
        );
    }
}
