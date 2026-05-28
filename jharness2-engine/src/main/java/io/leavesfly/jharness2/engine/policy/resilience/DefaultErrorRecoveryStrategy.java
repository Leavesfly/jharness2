package io.leavesfly.jharness2.engine.policy.resilience;

/**
 * 默认错误恢复策略：
 * <ul>
 *   <li>超时错误：最多重试 2 次</li>
 *   <li>其他错误：升级给 LLM 决策</li>
 *   <li>重试超过上限：升级给 LLM 决策</li>
 * </ul>
 */
public class DefaultErrorRecoveryStrategy implements ErrorRecoveryStrategy {

    private static final int MAX_RETRIES = 2;

    @Override
    public RecoveryDecision onToolError(String toolName, String error, int attemptCount) {
        if (attemptCount > MAX_RETRIES) {
            return RecoveryDecision.escalate(
                    "Tool '" + toolName + "' failed after " + attemptCount + " attempts: " + error);
        }

        // 超时类错误自动重试
        if (isTimeoutError(error)) {
            return RecoveryDecision.retry("Retrying due to timeout (attempt " + attemptCount + ")");
        }

        // 其余错误直接升级给 LLM
        return RecoveryDecision.defaultEscalate();
    }

    private boolean isTimeoutError(String error) {
        if (error == null) return false;
        String lower = error.toLowerCase();
        return lower.contains("timeout") || lower.contains("timed out") || lower.contains("超时");
    }
}
