package io.leavesfly.jharness2.core.engine.task;

import java.time.Instant;

public class BackgroundTask {

    public enum Status {
        RUNNING, COMPLETED, FAILED, CANCELLED
    }

    private final String taskId;
    private final String description;
    private final String command;
    private final Instant startedAt;
    private volatile Status status;
    private volatile String output;
    private volatile Instant completedAt;
    private Process process;

    public BackgroundTask(String taskId, String description, String command) {
        this.taskId = taskId;
        this.description = description;
        this.command = command;
        this.startedAt = Instant.now();
        this.status = Status.RUNNING;
    }

    public String getTaskId() { return taskId; }
    public String getDescription() { return description; }
    public String getCommand() { return command; }
    public Instant getStartedAt() { return startedAt; }
    public Status getStatus() { return status; }
    public String getOutput() { return output; }
    public Instant getCompletedAt() { return completedAt; }

    public void setStatus(Status status) { this.status = status; }
    public void setOutput(String output) { this.output = output; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setProcess(Process process) { this.process = process; }
    public Process getProcess() { return process; }
}
