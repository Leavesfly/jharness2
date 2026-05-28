package io.leavesfly.jharness2.core.quota;

/**
 * 配额策略 SPI —— 支持按用户等级配置不同的资源限额。
 * <p>
 * 实现方可根据用户身份、等级、租户等维度返回不同的配额配置。
 */
public interface QuotaPolicy {

    /**
     * 获取指定用户的配额限制。
     *
     * @param userId 用户标识
     * @return 该用户适用的配额限制
     */
    QuotaLimit getQuotaLimit(String userId);

    /**
     * 检查用户是否允许创建新的引擎。
     *
     * @param userId             用户标识
     * @param currentEngineCount 当前已有引擎数
     * @return 检查结果
     */
    QuotaCheckResult checkEngineCreation(String userId, int currentEngineCount);

    /**
     * 检查用户 token 用量是否超限。
     *
     * @param userId       用户标识
     * @param totalTokens  已使用的总 token 数
     * @return 检查结果
     */
    QuotaCheckResult checkTokenUsage(String userId, long totalTokens);
}
