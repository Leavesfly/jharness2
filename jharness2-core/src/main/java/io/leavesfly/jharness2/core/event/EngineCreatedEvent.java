package io.leavesfly.jharness2.core.event;

/**
 * 引擎创建事件。
 */
public class EngineCreatedEvent extends EngineEvent {

    private final String model;

    public EngineCreatedEvent(String userId, String sessionId, String model) {
        super(userId, sessionId);
        this.model = model;
    }

    public String getModel() { return model; }

    @Override
    public String getEventType() { return "engine.created"; }
}
