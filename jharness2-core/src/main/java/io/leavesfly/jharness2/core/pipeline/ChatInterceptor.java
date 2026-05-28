package io.leavesfly.jharness2.core.pipeline;

import io.leavesfly.jharness2.core.UserContext;

public interface ChatInterceptor {
    void beforeChat(UserContext context, String message);
    default void afterChat(UserContext context, String message, Throwable error) {}
    default int getOrder() { return 0; }
}
