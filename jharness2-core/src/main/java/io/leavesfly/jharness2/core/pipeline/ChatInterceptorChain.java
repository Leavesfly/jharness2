package io.leavesfly.jharness2.core.pipeline;

import io.leavesfly.jharness2.core.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatInterceptorChain {
    private static final Logger logger = LoggerFactory.getLogger(ChatInterceptorChain.class);
    private final List<ChatInterceptor> interceptors = new CopyOnWriteArrayList<>();

    public ChatInterceptorChain(List<ChatInterceptor> interceptors) {
        List<ChatInterceptor> sorted = interceptors.stream()
                .sorted(Comparator.comparingInt(ChatInterceptor::getOrder))
                .toList();
        this.interceptors.addAll(sorted);
    }

    public ChatInterceptorChain() {}

    public void addInterceptor(ChatInterceptor interceptor) {
        interceptors.add(interceptor);
        interceptors.sort(Comparator.comparingInt(ChatInterceptor::getOrder));
    }

    public void beforeChat(UserContext context, String message) {
        for (ChatInterceptor interceptor : interceptors) {
            try {
                interceptor.beforeChat(context, message);
            } catch (RuntimeException e) {
                logger.debug("Interceptor {} threw in beforeChat: {}", interceptor.getClass().getSimpleName(), e.getMessage());
                throw e;
            }
        }
    }

    public void afterChat(UserContext context, String message, Throwable error) {
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            try {
                interceptors.get(i).afterChat(context, message, error);
            } catch (Exception e) {
                logger.warn("Interceptor afterChat failed: {}", e.getMessage());
            }
        }
    }

    public int size() { return interceptors.size(); }
}
