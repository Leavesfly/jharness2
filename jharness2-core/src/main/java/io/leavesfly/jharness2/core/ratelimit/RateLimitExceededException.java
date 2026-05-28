package io.leavesfly.jharness2.core.ratelimit;

/**
 * 限流超限异常。
 */
public class RateLimitExceededException extends RuntimeException {

    private final String userId;
    private final int retryAfterSeconds;

    public RateLimitExceededException(String message, String userId, int retryAfterSeconds) {
        super(message);
        this.userId = userId;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getUserId() { return userId; }
    public int getRetryAfterSeconds() { return retryAfterSeconds; }
}
