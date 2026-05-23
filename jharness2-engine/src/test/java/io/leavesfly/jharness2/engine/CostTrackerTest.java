package io.leavesfly.jharness2.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CostTrackerTest {

    private CostTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new CostTracker();
    }

    @Test
    void shouldTrackInputAndOutputTokens() {
        tracker.addUsage(100, 200);

        assertEquals(100, tracker.getInputTokens());
        assertEquals(200, tracker.getOutputTokens());
        assertEquals(300, tracker.getTotalTokens());
    }

    @Test
    void shouldAccumulateUsage() {
        tracker.addUsage(100, 200);
        tracker.addUsage(50, 100);

        assertEquals(150, tracker.getInputTokens());
        assertEquals(300, tracker.getOutputTokens());
        assertEquals(450, tracker.getTotalTokens());
    }

    @Test
    void shouldSetAndGetModelName() {
        tracker.setModelName("gpt-4");
        assertEquals("gpt-4", tracker.getModelName());
    }

    @Test
    void shouldHaveDefaultModelName() {
        assertEquals("unknown", tracker.getModelName());
    }

    @Test
    void shouldResetCounters() {
        tracker.addUsage(100, 200);
        tracker.reset();

        assertEquals(0, tracker.getInputTokens());
        assertEquals(0, tracker.getOutputTokens());
        assertEquals(0, tracker.getTotalTokens());
    }

    @Test
    void shouldRestoreFromSnapshot() {
        tracker.addUsage(100, 200);
        tracker.restore(500, 600);

        assertEquals(500, tracker.getInputTokens());
        assertEquals(600, tracker.getOutputTokens());
        assertEquals(1100, tracker.getTotalTokens());
    }

    @Test
    void shouldCalculateSessionCostUsd() {
        tracker.addUsage(1000, 2000);

        double cost = tracker.getSessionCostUsd();
        // (1000 * 0.001 + 2000 * 0.002) / 1000.0 = (1 + 4) / 1000 = 0.005
        assertEquals(0.005, cost, 0.0001);
    }

    @Test
    void shouldHandleZeroUsage() {
        assertEquals(0, tracker.getInputTokens());
        assertEquals(0, tracker.getOutputTokens());
        assertEquals(0, tracker.getTotalTokens());
        assertEquals(0.0, tracker.getSessionCostUsd(), 0.0001);
    }

    @Test
    void shouldHandleLargeTokenCounts() {
        tracker.addUsage(1_000_000, 2_000_000);

        assertEquals(1_000_000, tracker.getInputTokens());
        assertEquals(2_000_000, tracker.getOutputTokens());
        assertEquals(3_000_000, tracker.getTotalTokens());
    }

    @Test
    void shouldMaintainSeparateCountersForMultipleAdds() {
        tracker.addUsage(10, 20);
        tracker.addUsage(30, 40);
        tracker.addUsage(50, 60);

        assertEquals(90, tracker.getInputTokens());
        assertEquals(120, tracker.getOutputTokens());
    }
}
