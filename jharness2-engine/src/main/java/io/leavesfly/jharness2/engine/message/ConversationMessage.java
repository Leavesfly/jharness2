package io.leavesfly.jharness2.engine.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConversationMessage {

    public enum Role {
        SYSTEM, USER, ASSISTANT, TOOL
    }

    private Role role;
    private String content;
    private List<ToolCall> toolCalls;
    private String toolCallId;
    private String name;

    public ConversationMessage() {}

    public static ConversationMessage system(String content) {
        ConversationMessage msg = new ConversationMessage();
        msg.role = Role.SYSTEM;
        msg.content = content;
        return msg;
    }

    public static ConversationMessage user(String content) {
        ConversationMessage msg = new ConversationMessage();
        msg.role = Role.USER;
        msg.content = content;
        return msg;
    }

    public static ConversationMessage assistant(String content) {
        ConversationMessage msg = new ConversationMessage();
        msg.role = Role.ASSISTANT;
        msg.content = content;
        return msg;
    }

    public static ConversationMessage assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        ConversationMessage msg = new ConversationMessage();
        msg.role = Role.ASSISTANT;
        msg.content = content;
        msg.toolCalls = toolCalls;
        return msg;
    }

    public static ConversationMessage toolResult(String toolCallId, String name, String content) {
        ConversationMessage msg = new ConversationMessage();
        msg.role = Role.TOOL;
        msg.toolCallId = toolCallId;
        msg.name = name;
        msg.content = content;
        return msg;
    }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public static class ToolCall {
        private String id;
        private String type;
        private FunctionCall function;

        public ToolCall() {}
        public ToolCall(String id, String type, FunctionCall function) {
            this.id = id;
            this.type = type;
            this.function = function;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public FunctionCall getFunction() { return function; }
        public void setFunction(FunctionCall function) { this.function = function; }
    }

    public static class FunctionCall {
        private String name;
        private String arguments;

        public FunctionCall() {}
        public FunctionCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getArguments() { return arguments; }
        public void setArguments(String arguments) { this.arguments = arguments; }
    }
}
