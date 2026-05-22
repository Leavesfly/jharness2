package io.leavesfly.jharness2.engine.cron;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CronExpressionTest {

    @Test
    void shouldMatchEveryMinute() {
        CronExpression cron = CronExpression.parse("* * * * *");
        LocalDateTime any = LocalDateTime.of(2026, 5, 22, 14, 30);
        assertTrue(cron.matches(any));
    }

    @Test
    void shouldMatchSpecificMinuteAndHour() {
        CronExpression cron = CronExpression.parse("30 14 * * *");
        assertTrue(cron.matches(LocalDateTime.of(2026, 5, 22, 14, 30)));
        assertFalse(cron.matches(LocalDateTime.of(2026, 5, 22, 14, 31)));
        assertFalse(cron.matches(LocalDateTime.of(2026, 5, 22, 15, 30)));
    }

    @Test
    void shouldMatchList() {
        CronExpression cron = CronExpression.parse("0,15,30,45 * * * *");
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 0)));
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 15)));
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 30)));
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 45)));
        assertFalse(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 10)));
    }

    @Test
    void shouldMatchRange() {
        CronExpression cron = CronExpression.parse("* 9-17 * * *");
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 9, 0)));
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 17, 0)));
        assertFalse(cron.matches(LocalDateTime.of(2026, 1, 1, 8, 0)));
        assertFalse(cron.matches(LocalDateTime.of(2026, 1, 1, 18, 0)));
    }

    @Test
    void shouldMatchStep() {
        CronExpression cron = CronExpression.parse("*/10 * * * *");
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 0)));
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 10)));
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 20)));
        assertFalse(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 5)));
    }

    @Test
    void shouldMatchDayOfWeek() {
        // 2026-05-22 is Friday = 5 in cron (0=Sun)
        CronExpression cron = CronExpression.parse("0 9 * * 5");
        assertTrue(cron.matches(LocalDateTime.of(2026, 5, 22, 9, 0)));
        assertFalse(cron.matches(LocalDateTime.of(2026, 5, 21, 9, 0))); // Thursday = 4
    }

    @Test
    void shouldMatchSunday() {
        // 2026-05-24 is Sunday = 0 in cron
        CronExpression cron = CronExpression.parse("0 0 * * 0");
        assertTrue(cron.matches(LocalDateTime.of(2026, 5, 24, 0, 0)));
    }

    @Test
    void shouldCalculateNextFireTime() {
        CronExpression cron = CronExpression.parse("30 14 * * *");
        LocalDateTime from = LocalDateTime.of(2026, 5, 22, 14, 0);
        Optional<LocalDateTime> next = cron.nextFireTime(from);
        assertTrue(next.isPresent());
        assertEquals(LocalDateTime.of(2026, 5, 22, 14, 30), next.get());
    }

    @Test
    void shouldCalculateNextFireTimeAcrossDay() {
        CronExpression cron = CronExpression.parse("0 9 * * *");
        LocalDateTime from = LocalDateTime.of(2026, 5, 22, 10, 0);
        Optional<LocalDateTime> next = cron.nextFireTime(from);
        assertTrue(next.isPresent());
        assertEquals(LocalDateTime.of(2026, 5, 23, 9, 0), next.get());
    }

    @Test
    void shouldRejectInvalidExpressions() {
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse(""));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("60 * * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* 25 * * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * 32 * *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * * 13 *"));
        assertThrows(IllegalArgumentException.class, () -> CronExpression.parse("* * * * 7"));
    }

    @Test
    void shouldSupportRangeWithStep() {
        CronExpression cron = CronExpression.parse("1-30/5 * * * *");
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 1)));
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 6)));
        assertTrue(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 11)));
        assertFalse(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 0)));
        assertFalse(cron.matches(LocalDateTime.of(2026, 1, 1, 0, 2)));
    }
}
