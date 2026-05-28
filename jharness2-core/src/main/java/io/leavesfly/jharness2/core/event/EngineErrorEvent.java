package io.leavesfly.jharness2.core.event;

/**
 * 引擎错误事件。
 */
public class EngineErrorEvent extends EngineEvent {

    private final String errorMessage;
    private final String errorType;

    public EngineErrorEvent(String userId, String sessionId,
                            String errorMessage, String errorType) {
        super(userId, sessionId);
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }

    public String getErrorMessage() { return errorMessage; }
    public String getErrorType() { return errorType; }

    @Override
    public String getEventType() { return "engine.error"; }
}
