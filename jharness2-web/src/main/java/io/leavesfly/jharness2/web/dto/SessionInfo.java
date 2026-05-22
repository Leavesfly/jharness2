package io.leavesfly.jharness2.web.dto;

import java.time.Instant;

public class SessionInfo {
    private String sessionId;
    private String model;
    private String title;
    private int messageCount;
    private Instant createdAt;
    private Instant updatedAt;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
