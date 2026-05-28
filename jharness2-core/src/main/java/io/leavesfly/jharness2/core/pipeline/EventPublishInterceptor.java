package io.leavesfly.jharness2.core.pipeline;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.core.event.EngineErrorEvent;
import io.leavesfly.jharness2.core.event.EngineEventBus;

public class EventPublishInterceptor implements ChatInterceptor {
    private final EngineEventBus eventBus;

    public EventPublishInterceptor(EngineEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void beforeChat(UserContext context, String message) {
        // no-op
    }

    @Override
    public void afterChat(UserContext context, String message, Throwable error) {
        if (error != null) {
            eventBus.publish(new EngineErrorEvent(
                    context.getUserId(), context.getSessionId(),
                    error.getMessage(), error.getClass().getSimpleName()));
        }
    }

    @Override
    public int getOrder() { return 200; }
}
