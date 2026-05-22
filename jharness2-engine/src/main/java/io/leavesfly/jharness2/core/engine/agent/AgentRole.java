package io.leavesfly.jharness2.core.engine.agent;

public class AgentRole {
    private final String name;
    private final String systemPrompt;
    private final String model;

    public AgentRole(String name, String systemPrompt, String model) {
        this.name = name;
        this.systemPrompt = systemPrompt;
        this.model = model;
    }

    public String getName() { return name; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getModel() { return model; }
}
