package io.leavesfly.jharness2.engine.ext.evolution;

import io.leavesfly.jharness2.engine.ext.hook.HookEvent;

/**
 * 进化子系统相关的 HookEvent 常量定义。
 */
public final class EvolutionEvent {

    private EvolutionEvent() {}

    /** 经验提取完成 */
    public static final HookEvent EXPERIENCE_EXTRACTED = HookEvent.of("evolution_experience_extracted");

    /** 工具生成完成 */
    public static final HookEvent TOOL_CREATED = HookEvent.of("evolution_tool_created");

    /** 策略指令更新 */
    public static final HookEvent DIRECTIVE_UPDATED = HookEvent.of("evolution_directive_updated");

    /** 策略指令回滚 */
    public static final HookEvent DIRECTIVE_ROLLED_BACK = HookEvent.of("evolution_directive_rolled_back");
}
