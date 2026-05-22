package io.leavesfly.jharness2.core;

public interface EngineFactory {
    EngineInstance create(UserContext context);
}
