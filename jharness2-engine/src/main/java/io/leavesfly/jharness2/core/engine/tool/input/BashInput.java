package io.leavesfly.jharness2.core.engine.tool.input;

public class BashInput {
    private String command;
    private Integer timeout_seconds;

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public Integer getTimeout_seconds() { return timeout_seconds; }
    public void setTimeout_seconds(Integer timeout_seconds) { this.timeout_seconds = timeout_seconds; }
}
