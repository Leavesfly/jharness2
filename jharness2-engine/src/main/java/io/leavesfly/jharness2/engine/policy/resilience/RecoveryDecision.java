package io.leavesfly.jharness2.engine.policy.resilience;

/**
 * 恢复决策 — ErrorRecoveryStrategy 返回的决策结果。
 *
 * @param action        恢复动作
 * @param fallbackTool  降级工具名称（仅 FALLBACK 时有效）
 * @param message       附加说明（用于日志或传递给 LLM）
 */
public record RecoveryDecision(
        RecoveryAction action,
        String fallbackTool,
        String message
) {
    public static RecoveryDecision retry(String message) {
        return new RecoveryDecision(RecoveryAction.RETRY, null, message);
    }

    public static RecoveryDecision fallback(String fallbackTool, String message) {
        return new RecoveryDecision(RecoveryAction.FALLBACK, fallbackTool, message);
    }

    public static RecoveryDecision escalate(String message) {
        return new RecoveryDecision(RecoveryAction.ESCALATE, null, message);
    }

    public static RecoveryDecision skip(String message) {
        return new RecoveryDecision(RecoveryAction.SKIP, null, message);
    }

    public static RecoveryDecision abort(String message) {
        return new RecoveryDecision(RecoveryAction.ABORT, null, message);
    }

    /** 默认策略：升级给 LLM 自行决策 */
    public static RecoveryDecision defaultEscalate() {
        return escalate("Tool execution failed, delegating to LLM for next action.");
    }
}
