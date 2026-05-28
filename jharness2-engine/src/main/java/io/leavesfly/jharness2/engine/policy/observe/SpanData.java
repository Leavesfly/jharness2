package io.leavesfly.jharness2.engine.policy.observe;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Span 的不可变快照数据 — 用于导出、序列化和分析。
 */
public class SpanData {

    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final long startTimeMs;
    private final long endTimeMs;
    private final long durationMs;
    private final SpanStatus status;
    private final Map<String, Object> attributes;
    private final String errorMessage;
    private final List<SpanData> children;

    public SpanData(String spanId, String parentSpanId, String name,
                    long startTimeMs, long endTimeMs, SpanStatus status,
                    Map<String, Object> attributes, String errorMessage,
                    List<SpanData> children) {
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.startTimeMs = startTimeMs;
        this.endTimeMs = endTimeMs;
        this.durationMs = endTimeMs - startTimeMs;
        this.status = status;
        this.attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
        this.errorMessage = errorMessage;
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    public String getSpanId() { return spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public String getName() { return name; }
    public long getStartTimeMs() { return startTimeMs; }
    public long getEndTimeMs() { return endTimeMs; }
    public long getDurationMs() { return durationMs; }
    public SpanStatus getStatus() { return status; }
    public Map<String, Object> getAttributes() { return attributes; }
    public String getErrorMessage() { return errorMessage; }
    public List<SpanData> getChildren() { return children; }

    @Override
    public String toString() {
        return String.format("Span{name='%s', duration=%dms, status=%s, children=%d}",
                name, durationMs, status, children.size());
    }
}
