package io.leavesfly.jharness2.engine.policy.resilience;

/**
 * 错误恢复动作 — 引擎在工具执行出错时可选择的应对策略。
 */
public enum RecoveryAction {
    /** 自动重试（使用相同参数） */
    RETRY,
    /** 降级到备选工具 */
    FALLBACK,
    /** 升级给用户确认（将错误暴露给 LLM 决策） */
    ESCALATE,
    /** 跳过此工具，继续执行后续工具 */
    SKIP,
    /** 中止整个 ReAct 循环 */
    ABORT
}
