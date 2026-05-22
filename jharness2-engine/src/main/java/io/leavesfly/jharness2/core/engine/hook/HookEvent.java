package io.leavesfly.jharness2.core.engine.hook;

public enum HookEvent {
    SESSION_START("session_start"),
    SESSION_END("session_end"),
    USER_PROMPT_SUBMIT("user_prompt_submit"),
    STOP("stop"),
    PRE_TOOL_USE("pre_tool_use"),
    POST_TOOL_USE("post_tool_use"),
    SUBAGENT_STOP("subagent_stop"),
    NOTIFICATION("notification");

    private final String value;

    HookEvent(String value) {
        this.value = value;
    }

    public String getValue() { return value; }
}
