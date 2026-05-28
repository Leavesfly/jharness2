package io.leavesfly.jharness2.core.quota;

/**
 * 配额超限异常 —— 当用户超出配额时抛出。
 */
public class QuotaExceededException extends RuntimeException {

    private final String userId;
    private final String quotaType;

    public QuotaExceededException(String message, String userId, String quotaType) {
        super(message);
        this.userId = userId;
        this.quotaType = quotaType;
    }

    public String getUserId() { return userId; }
    public String getQuotaType() { return quotaType; }
}
