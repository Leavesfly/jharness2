package io.leavesfly.jharness2.core.engine.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class HookExecutor {

    private static final Logger logger = LoggerFactory.getLogger(HookExecutor.class);
    private final Map<HookEvent, List<HookHandler>> handlers = new ConcurrentHashMap<>();

    public void register(HookEvent event, HookHandler handler) {
        handlers.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    public void unregister(HookEvent event, HookHandler handler) {
        List<HookHandler> list = handlers.get(event);
        if (list != null) list.remove(handler);
    }

    public CompletableFuture<Void> fire(HookEvent event, Map<String, Object> payload) {
        return CompletableFuture.runAsync(() -> {
            List<HookHandler> list = handlers.get(event);
            if (list == null || list.isEmpty()) return;

            for (HookHandler handler : list) {
                try {
                    handler.handle(event, payload);
                } catch (Exception e) {
                    logger.warn("Hook handler failed for event '{}': {}", event.getValue(), e.getMessage());
                }
            }
        });
    }

    public int handlerCount(HookEvent event) {
        List<HookHandler> list = handlers.get(event);
        return list != null ? list.size() : 0;
    }

    public int totalHandlers() {
        return handlers.values().stream().mapToInt(List::size).sum();
    }
}
