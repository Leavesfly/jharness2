package io.leavesfly.jharness2.engine.tool.input;

import java.util.List;

/**
 * Sub-Agent 工具输入。
 * - tasks: 要分派给子 Agent 的任务列表
 * - mode: "parallel"（并行）或 "sequential"（串行），默认 parallel
 */
public class SubAgentInput {

    private List<TaskItem> tasks;
    private String mode;

    public List<TaskItem> getTasks() { return tasks; }
    public void setTasks(List<TaskItem> tasks) { this.tasks = tasks; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public static class TaskItem {
        private String description;
        private String prompt;

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPrompt() { return prompt; }
        public void setPrompt(String prompt) { this.prompt = prompt; }
    }
}
