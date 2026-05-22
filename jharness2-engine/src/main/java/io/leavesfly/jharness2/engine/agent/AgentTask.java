package io.leavesfly.jharness2.engine.agent;

public class AgentTask {
    private final String description;
    private final String prompt;
    private final AgentRole role;

    public AgentTask(String description, String prompt, AgentRole role) {
        this.description = description;
        this.prompt = prompt;
        this.role = role;
    }

    public String getDescription() { return description; }
    public String getPrompt() { return prompt; }
    public AgentRole getRole() { return role; }
}
