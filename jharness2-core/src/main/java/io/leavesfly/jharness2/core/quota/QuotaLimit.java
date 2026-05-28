package io.leavesfly.jharness2.core.quota;

/**
 * 用户配额限制定义。
 */
public class QuotaLimit {

    private final int maxEngines;
    private final long maxTokensPerDay;
    private final int maxConcurrentRequests;
    private final int maxRequestsPerMinute;

    public QuotaLimit(int maxEngines, long maxTokensPerDay,
                      int maxConcurrentRequests, int maxRequestsPerMinute) {
        this.maxEngines = maxEngines;
        this.maxTokensPerDay = maxTokensPerDay;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public int getMaxEngines() { return maxEngines; }
    public long getMaxTokensPerDay() { return maxTokensPerDay; }
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }

    /** 默认配额（对应 EngineConfig 中的默认值） */
    public static QuotaLimit defaultLimit() {
        return new QuotaLimit(5, 1_000_000L, 3, 30);
    }

    /** 高级用户配额 */
    public static QuotaLimit premiumLimit() {
        return new QuotaLimit(20, 10_000_000L, 10, 100);
    }
}
