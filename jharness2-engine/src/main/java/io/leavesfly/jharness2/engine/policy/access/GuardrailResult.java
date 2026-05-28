package io.leavesfly.jharness2.engine.policy.access;

/**
 * Guardrail 检查结果。
 *
 * @param passed           是否通过检查
 * @param tripwire         是否触发熔断（true 则立即中止 ReAct 循环）
 * @param reason           拒绝/警告原因（通过时为 null）
 * @param correctedContent 修正后的内容（仅 output guardrail 可用，null 表示不修正）
 */
public record GuardrailResult(
        boolean passed,
        boolean tripwire,
        String reason,
        String correctedContent
) {
    /** 检查通过 */
    public static GuardrailResult pass() {
        return new GuardrailResult(true, false, null, null);
    }

    /** 检查未通过但不熔断（记录警告，继续执行） */
    public static GuardrailResult warn(String reason) {
        return new GuardrailResult(false, false, reason, null);
    }

    /** 触发熔断 — 立即中止循环 */
    public static GuardrailResult tripwire(String reason) {
        return new GuardrailResult(false, true, reason, null);
    }

    /** 检查未通过，但提供修正内容（用于 output guardrail） */
    public static GuardrailResult correct(String reason, String correctedContent) {
        return new GuardrailResult(false, false, reason, correctedContent);
    }
}
