package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.engine.QueryEngine;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public class EngineInstance {
    private final QueryEngine engine;
    private final UserContext userContext;
    private final Instant createdAt;
    private final AtomicReference<Instant> lastAccessedAt;

    public EngineInstance(QueryEngine engine, UserContext userContext) {
        this.engine = engine;
        this.userContext = userContext;
        this.createdAt = Instant.now();
        this.lastAccessedAt = new AtomicReference<>(this.createdAt);
    }

    public QueryEngine getEngine() {
        lastAccessedAt.set(Instant.now());
        return engine;
    }

    public UserContext getUserContext() { return userContext; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt.get(); }

    public void close() {
        engine.close();
    }
}
