package io.leavesfly.jharness2.engine.hook;

import java.util.Map;

@FunctionalInterface
public interface HookHandler {
    void handle(HookEvent event, Map<String, Object> payload) throws Exception;
}
