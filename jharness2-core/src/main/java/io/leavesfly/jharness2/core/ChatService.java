package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.core.pipeline.ChatInterceptorChain;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.stream.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final UserEngineRegistry engineRegistry;
    private final ChatInterceptorChain interceptorChain;

    public ChatService(UserEngineRegistry engineRegistry,
                       @Autowired(required = false) ChatInterceptorChain interceptorChain) {
        this.engineRegistry = engineRegistry;
        this.interceptorChain = interceptorChain != null ? interceptorChain : new ChatInterceptorChain();
    }

    /**
     * 发送消息并通过类型安全的适配器将流式事件转换为 DTO 回调。
     * 自动跟踪活跃请求数，支持优雅关闭时等待完成。
     */
    public CompletableFuture<Void> chat(UserContext context, String message,
                                        Consumer<ChatEventDto> eventConsumer) {
        interceptorChain.beforeChat(context, message);
        
        EngineInstance instance = engineRegistry.getOrCreate(context);
        QueryEngine engine = instance.getEngine();
        instance.acquireRequest();
        logger.debug("Submitting message for user={}, session={}",
                context.getUserId(), context.getSessionId());

        return engine.submitMessage(message, (StreamEvent event) -> {
            ChatEventDto dto = StreamEventAdapter.adapt(event);
            if (dto != null) {
                eventConsumer.accept(dto);
            }
        }).whenComplete((result, error) -> {
            instance.releaseRequest();
            interceptorChain.afterChat(context, message, error);
        });
    }

    public void cancelChat(String userId, String sessionId) {
        EngineInstance instance = engineRegistry.get(userId, sessionId);
        if (instance != null) {
            instance.getEngine().cancel();
            logger.info("Cancelled chat for user={}, session={}", userId, sessionId);
        }
    }
}
