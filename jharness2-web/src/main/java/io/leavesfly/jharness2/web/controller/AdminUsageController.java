package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.core.spi.ModelUsageSummary;
import io.leavesfly.jharness2.core.spi.UsageStore;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 用量查询管理接口 —— 提供用户用量统计和历史查询。
 */
@RestController
@RequestMapping("/api/admin/usage")
public class AdminUsageController {

    private final UsageStore usageStore;

    public AdminUsageController(UsageStore usageStore) {
        this.usageStore = usageStore;
    }

    /**
     * 查询当前用户今日 token 用量。
     */
    @GetMapping("/today")
    public ResponseEntity<?> todayUsage(Authentication auth) {
        String userId = auth.getName();
        LocalDate today = LocalDate.now();
        long tokens = usageStore.getDailyTokens(userId, today);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "date", today.toString(),
                "totalTokens", tokens
        ));
    }

    /**
     * 查询指定用户指定日期的 token 用量。
     */
    @GetMapping("/daily")
    public ResponseEntity<?> dailyUsage(Authentication auth,
                                         @RequestParam(required = false) String userId,
                                         @RequestParam String date) {
        String targetUserId = (userId != null) ? userId : auth.getName();
        LocalDate targetDate = LocalDate.parse(date);
        long tokens = usageStore.getDailyTokens(targetUserId, targetDate);
        return ResponseEntity.ok(Map.of(
                "userId", targetUserId,
                "date", targetDate.toString(),
                "totalTokens", tokens
        ));
    }

    /**
     * 查询指定用户指定月份的 token 用量。
     */
    @GetMapping("/monthly")
    public ResponseEntity<?> monthlyUsage(Authentication auth,
                                           @RequestParam(required = false) String userId,
                                           @RequestParam int year,
                                           @RequestParam int month) {
        String targetUserId = (userId != null) ? userId : auth.getName();
        long tokens = usageStore.getMonthlyTokens(targetUserId, year, month);
        return ResponseEntity.ok(Map.of(
                "userId", targetUserId,
                "year", year,
                "month", month,
                "totalTokens", tokens
        ));
    }

    /**
     * 查询当前用户的模型用量汇总。
     */
    @GetMapping("/summary")
    public ResponseEntity<?> usageSummary(Authentication auth,
                                           @RequestParam(required = false) String userId,
                                           @RequestParam int year,
                                           @RequestParam int month) {
        String targetUserId = (userId != null) ? userId : auth.getName();
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.plusMonths(1);
        List<ModelUsageSummary> summaries = usageStore.getModelUsageSummary(targetUserId, from, to);
        return ResponseEntity.ok(Map.of(
                "userId", targetUserId,
                "year", year,
                "month", month,
                "models", summaries
        ));
    }
}
