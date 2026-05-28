package io.leavesfly.jharness2.engine.policy.resilience;

/**
 * 错误恢复策略接口 — 定义工具执行失败时的引擎级应对。
 * <p>
 * 与直接将错误返回给 LLM（当前默认行为）不同，ErrorRecoveryStrategy 允许引擎
 * 在工具出错时主动干预，而非完全依赖 LLM 自行决策。
 *
 * <p>典型场景：
 * <ul>
 *   <li>网络超时 → 自动重试</li>
 *   <li>文件不存在 → 降级为搜索工具</li>
 *   <li>权限拒绝 → 中止并通知用户</li>
 *   <li>未知错误 → 升级给 LLM 决策（默认行为）</li>
 * </ul>
 */
@FunctionalInterface
public interface ErrorRecoveryStrategy {

    /**
     * 决定如何处理工具执行错误。
     *
     * @param toolName     出错的工具名称
     * @param error        错误信息
     * @param attemptCount 已尝试次数（首次失败为 1）
     * @return 恢复决策
     */
    RecoveryDecision onToolError(String toolName, String error, int attemptCount);
}
