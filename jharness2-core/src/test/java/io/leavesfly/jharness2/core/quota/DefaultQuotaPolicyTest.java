package io.leavesfly.jharness2.core.quota;

import io.leavesfly.jharness2.core.EngineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultQuotaPolicyTest {

    private DefaultQuotaPolicy policy;
    private EngineConfig config;

    @BeforeEach
    void setUp() {
        config = new EngineConfig();
        config.setMaxEnginesPerUser(5);
        policy = new DefaultQuotaPolicy(config);
    }

    @Test
    void shouldAllowEngineCreationWithinLimit() {
        QuotaCheckResult result = policy.checkEngineCreation("user1", 3);
        assertTrue(result.isAllowed());
    }

    @Test
    void shouldDenyEngineCreationAtLimit() {
        QuotaCheckResult result = policy.checkEngineCreation("user1", 5);
        assertTrue(result.isDenied());
        assertNotNull(result.getReason());
    }

    @Test
    void shouldAllowTokenUsageWithinLimit() {
        QuotaCheckResult result = policy.checkTokenUsage("user1", 500_000);
        assertTrue(result.isAllowed());
    }

    @Test
    void shouldDenyTokenUsageOverLimit() {
        QuotaCheckResult result = policy.checkTokenUsage("user1", 1_000_001);
        assertTrue(result.isDenied());
    }

    @Test
    void quotaLimitShouldReflectConfig() {
        QuotaLimit limit = policy.getQuotaLimit("user1");
        assertEquals(5, limit.getMaxEngines());
        assertEquals(1_000_000L, limit.getMaxTokensPerDay());
    }
}
