package io.leavesfly.jharness2.engine.policy.resilience;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行策略 — 精细控制每个工具的执行行为。
 * <p>
 * 与全局统一的 5 分钟超时不同，ToolExecutionPolicy 支持：
 * <ul>
 *   <li>每个工具独立的超时时间</li>
 *   <li>最大重试次数配置</li>
 *   <li>是否允许中断（cancel）</li>
 *   <li>是否需要沙箱隔离</li>
 * </ul>
 */
public class ToolExecutionPolicy {

    private final Duration defaultTimeout;
    private final int defaultMaxRetries;
    private final Map<String, ToolPolicy> toolPolicies = new ConcurrentHashMap<>();

    public ToolExecutionPolicy() {
        this(Duration.ofMinutes(5), 0);
    }

    public ToolExecutionPolicy(Duration defaultTimeout, int defaultMaxRetries) {
        this.defaultTimeout = defaultTimeout;
        this.defaultMaxRetries = defaultMaxRetries;
    }

    /** 为指定工具设置策略 */
    public void setPolicy(String toolName, ToolPolicy policy) {
        toolPolicies.put(toolName, policy);
    }

    /** 获取指定工具的超时时间 */
    public Duration getTimeout(String toolName) {
        ToolPolicy policy = toolPolicies.get(toolName);
        return policy != null && policy.timeout() != null ? policy.timeout() : defaultTimeout;
    }

    /** 获取指定工具的最大重试次数 */
    public int getMaxRetries(String toolName) {
        ToolPolicy policy = toolPolicies.get(toolName);
        return policy != null ? policy.maxRetries() : defaultMaxRetries;
    }

    /** 判断指定工具是否可被中断 */
    public boolean isCancellable(String toolName) {
        ToolPolicy policy = toolPolicies.get(toolName);
        return policy == null || policy.cancellable();
    }

    /** 判断指定工具是否需要沙箱隔离执行 */
    public boolean requiresSandbox(String toolName) {
        ToolPolicy policy = toolPolicies.get(toolName);
        return policy != null && policy.sandboxed();
    }

    public Duration getDefaultTimeout() { return defaultTimeout; }
    public int getDefaultMaxRetries() { return defaultMaxRetries; }

    /**
     * 单个工具的策略定义。
     *
     * @param timeout     超时时间（null 使用默认值）
     * @param maxRetries  最大重试次数
     * @param cancellable 是否可中断
     * @param sandboxed   是否需要沙箱隔离
     */
    public record ToolPolicy(
            Duration timeout,
            int maxRetries,
            boolean cancellable,
            boolean sandboxed
    ) {
        /** 快速构建：仅指定超时 */
        public static ToolPolicy withTimeout(Duration timeout) {
            return new ToolPolicy(timeout, 0, true, false);
        }

        /** 快速构建：需要沙箱的高危工具 */
        public static ToolPolicy sandboxed(Duration timeout) {
            return new ToolPolicy(timeout, 0, true, true);
        }

        /** 快速构建：允许重试的工具 */
        public static ToolPolicy retriable(Duration timeout, int maxRetries) {
            return new ToolPolicy(timeout, maxRetries, true, false);
        }
    }
}
