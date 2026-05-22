package io.leavesfly.jharness2.core.distributed;

import io.leavesfly.jharness2.engine.ConversationMessage;

import java.time.Instant;
import java.util.List;

/**
 * 引擎的可序列化状态快照，用于 Redis 存储和跨节点恢复。
 */
public class EngineState {

    private String userId;
    private String sessionId;
    private String model;
    private String baseUrl;
    private String apiKey;
    private String workspacePath;
    private List<ConversationMessage> messages;
    private long inputTokens;
    private long outputTokens;
    private boolean cancelled;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private String ownerNodeId;

    public EngineState() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getWorkspacePath() { return workspacePath; }
    public void setWorkspacePath(String workspacePath) { this.workspacePath = workspacePath; }
    public List<ConversationMessage> getMessages() { return messages; }
    public void setMessages(List<ConversationMessage> messages) { this.messages = messages; }
    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
    public String getOwnerNodeId() { return ownerNodeId; }
    public void setOwnerNodeId(String ownerNodeId) { this.ownerNodeId = ownerNodeId; }

    /**
     * 从引擎实例捕获状态快照。
     */
    public static EngineState capture(io.leavesfly.jharness2.core.EngineInstance instance, String nodeId) {
        EngineState state = new EngineState();
        var context = instance.getUserContext();
        var engine = instance.getEngine();

        state.setUserId(context.getUserId());
        state.setSessionId(context.getSessionId());
        state.setModel(context.getModel());
        state.setBaseUrl(context.getBaseUrl());
        state.setApiKey(context.getApiKey());
        state.setWorkspacePath(context.getWorkspace() != null ? context.getWorkspace().toString() : null);
        state.setMessages(engine.getMessages());
        state.setInputTokens(engine.getCostTracker().getInputTokens());
        state.setOutputTokens(engine.getCostTracker().getOutputTokens());
        state.setCancelled(false);
        state.setCreatedAt(instance.getCreatedAt());
        state.setLastAccessedAt(instance.getLastAccessedAt());
        state.setOwnerNodeId(nodeId);

        return state;
    }
}
