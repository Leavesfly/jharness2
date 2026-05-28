package io.leavesfly.jharness2.core.quota;

/**
 * 配额检查结果。
 */
public class QuotaCheckResult {

    private final boolean allowed;
    private final String reason;

    private QuotaCheckResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static QuotaCheckResult allow() {
        return new QuotaCheckResult(true, null);
    }

    public static QuotaCheckResult deny(String reason) {
        return new QuotaCheckResult(false, reason);
    }

    public boolean isAllowed() { return allowed; }
    public boolean isDenied() { return !allowed; }
    public String getReason() { return reason; }
}
