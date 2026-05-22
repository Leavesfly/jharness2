package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.core.engine.QueryEngine;
import io.leavesfly.jharness2.core.engine.stream.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final UserEngineRegistry engineRegistry;

    public ChatService(UserEngineRegistry engineRegistry) {
        this.engineRegistry = engineRegistry;
    }

    /**
     * 发送消息并通过类型安全的适配器将流式事件转换为 DTO 回调。
     */
    public CompletableFuture<Void> chat(UserContext context, String message,
                                        Consumer<ChatEventDto> eventConsumer) {
        EngineInstance instance = engineRegistry.getOrCreate(context);
        QueryEngine engine = instance.getEngine();
        logger.debug("Submitting message for user={}, session={}",
                context.getUserId(), context.getSessionId());

        return engine.submitMessage(message, (StreamEvent event) -> {
            ChatEventDto dto = StreamEventAdapter.adapt(event);
            if (dto != null) {
                eventConsumer.accept(dto);
            }
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
