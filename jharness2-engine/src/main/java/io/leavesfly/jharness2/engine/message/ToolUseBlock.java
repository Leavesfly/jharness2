package io.leavesfly.jharness2.engine.message;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具使用内容块 —— 表示 LLM 请求调用某个工具。
 */
public class ToolUseBlock {
    private final String id;
    private final String name;
    private final JsonNode input;

    public ToolUseBlock(String id, String name, JsonNode input) {
        this.id = id;
        this.name = name;
        this.input = input;
    }

    public String getType() { return "tool_use"; }

    public String getId() { return id; }
    public String getName() { return name; }
    public JsonNode getInput() { return input; }

    @Override
    public String toString() {
        return "ToolUseBlock{id='" + id + "', name='" + name + "', input=" + input + "}";
    }
}
