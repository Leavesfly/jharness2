package io.leavesfly.jharness2.engine.policy.observe;

import java.util.Map;

/**
 * 追踪 Span — 表示一段可观测的执行单元（如一轮 ReAct 循环、一次工具调用、一次 LLM 请求）。
 * <p>
 * 设计参考 OpenTelemetry Span 语义，但保持最小化接口，不引入外部依赖。
 */
public interface Span extends AutoCloseable {

    /** Span 唯一标识 */
    String getSpanId();

    /** Span 名称（如 "react-turn-1"、"tool:read_file"） */
    String getName();

    /** 设置属性（键值对，用于记录参数、结果等元信息） */
    void setAttribute(String key, Object value);

    /** 批量设置属性 */
    void setAttributes(Map<String, Object> attributes);

    /** 设置 Span 状态 */
    void setStatus(SpanStatus status);

    /** 设置错误信息 */
    void setError(Throwable throwable);

    /** 创建子 Span */
    Span startChild(String name);

    /** 创建带属性的子 Span */
    Span startChild(String name, Map<String, Object> attributes);

    /** 获取 Span 开始时间（epoch millis） */
    long getStartTimeMs();

    /** 获取 Span 持续时间（millis），未关闭时返回当前已经过时间 */
    long getDurationMs();

    /** Span 是否已关闭 */
    boolean isEnded();

    /** 关闭 Span（记录结束时间） */
    @Override
    void close();
}
