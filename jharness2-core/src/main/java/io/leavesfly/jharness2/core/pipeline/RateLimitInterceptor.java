package io.leavesfly.jharness2.core.pipeline;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.ratelimit.RateLimitExceededException;
import io.leavesfly.jharness2.core.ratelimit.RateLimiter;

public class RateLimitInterceptor implements ChatInterceptor {
    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void beforeChat(UserContext context, String message) {
        boolean allowed = rateLimiter.tryAcquire(context.getUserId());
        if (!allowed) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded for user: " + context.getUserId(),
                    context.getUserId(), 60);
        }
    }

    @Override
    public int getOrder() { return 50; }
}
