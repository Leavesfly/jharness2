package io.leavesfly.jharness2.engine.tool.input;

public class GrepInput {
    private String pattern;
    private String path;
    private Boolean include_hidden;

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public Boolean getInclude_hidden() { return include_hidden != null && include_hidden; }
    public void setInclude_hidden(Boolean include_hidden) { this.include_hidden = include_hidden; }
}
