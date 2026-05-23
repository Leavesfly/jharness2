package io.leavesfly.jharness2.engine.hook;

/**
 * Hook 事件定义 — 使用字符串标识而非封闭枚举，支持扩展方自定义事件。
 * <p>
 * 内置事件作为常量提供，外部可通过 {@link #of(String)} 创建自定义事件。
 */
public final class HookEvent {

    // --- 引擎核心生命周期事件 ---
    public static final HookEvent SESSION_START = new HookEvent("session_start");
    public static final HookEvent SESSION_END = new HookEvent("session_end");
    public static final HookEvent USER_PROMPT_SUBMIT = new HookEvent("user_prompt_submit");
    public static final HookEvent STOP = new HookEvent("stop");

    // --- 工具执行事件 ---
    public static final HookEvent PRE_TOOL_USE = new HookEvent("pre_tool_use");
    public static final HookEvent POST_TOOL_USE = new HookEvent("post_tool_use");

    // --- Sub-Agent 事件 ---
    public static final HookEvent SUBAGENT_STOP = new HookEvent("subagent_stop");

    // --- 通知事件 ---
    public static final HookEvent NOTIFICATION = new HookEvent("notification");

    private final String value;

    private HookEvent(String value) {
        this.value = value;
    }

    /**
     * 创建自定义 Hook 事件（供扩展方使用）。
     * <p>
     * 例如 heartbeat、cron_trigger 等平台级事件可在 core 模块中定义：
     * <pre>
     *   HookEvent HEARTBEAT = HookEvent.of("heartbeat");
     *   HookEvent CRON_TRIGGER = HookEvent.of("cron_trigger");
     * </pre>
     */
    public static HookEvent of(String value) {
        return new HookEvent(value);
    }

    public String getValue() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HookEvent that)) return false;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "HookEvent{" + value + "}";
    }
}

