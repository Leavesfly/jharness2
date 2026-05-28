package io.leavesfly.jharness2.engine.policy.observe;

import java.util.Map;

/**
 * 追踪器接口 — 创建和管理 Span 的入口。
 * <p>
 * 引擎核心仅依赖此接口，具体实现可以是：
 * <ul>
 *   <li>{@link DefaultTracer} — 内存存储，适用于调试和评估</li>
 *   <li>OpenTelemetry 适配器 — 对接外部可观测平台</li>
 *   <li>{@link NoopTracer} — 无操作实现，零开销</li>
 * </ul>
 */
public interface Tracer {

    /** 创建根 Span */
    Span startSpan(String name);

    /** 创建带属性的根 Span */
    Span startSpan(String name, Map<String, Object> attributes);

    /** 获取当前活跃的 Span（可用于子模块自动挂载子 Span） */
    Span currentSpan();

    /** 导出所有已完成的 Span（用于分析/回放） */
    java.util.List<SpanData> exportSpans();

    /** 清空已记录的 Span */
    void clear();
}
