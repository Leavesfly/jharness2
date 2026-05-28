package io.leavesfly.jharness2.engine.ext.cron;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 轻量级 Cron 表达式解析器，支持标准 5 段格式：
 * <pre>
 *   分钟 小时 日 月 星期
 *   *    *    *  *  *
 * </pre>
 * 支持的语法：
 * <ul>
 *   <li>{@code *} - 匹配所有值</li>
 *   <li>{@code 5} - 精确匹配</li>
 *   <li>{@code 1,3,5} - 列表</li>
 *   <li>{@code 1-5} - 范围</li>
 *   <li>{@code * /10} - 步进（不含空格）</li>
 * </ul>
 */
public class CronExpression {

    private final String expression;
    private final Set<Integer> minutes;
    private final Set<Integer> hours;
    private final Set<Integer> daysOfMonth;
    private final Set<Integer> months;
    private final Set<Integer> daysOfWeek;

    private CronExpression(String expression, Set<Integer> minutes, Set<Integer> hours,
                           Set<Integer> daysOfMonth, Set<Integer> months, Set<Integer> daysOfWeek) {
        this.expression = expression;
        this.minutes = minutes;
        this.hours = hours;
        this.daysOfMonth = daysOfMonth;
        this.months = months;
        this.daysOfWeek = daysOfWeek;
    }

    /**
     * 解析 cron 表达式字符串。
     *
     * @param expression 标准 5 段 cron 表达式
     * @return 解析后的 CronExpression 对象
     * @throws IllegalArgumentException 表达式格式错误
     */
    public static CronExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Cron expression must not be blank");
        }

        String[] parts = expression.trim().split("\\s+");
        if (parts.length != 5) {
            throw new IllegalArgumentException(
                    "Cron expression must have 5 fields (minute hour day month weekday), got " + parts.length + ": " + expression);
        }

        Set<Integer> minutes = parseField(parts[0], 0, 59, "minute");
        Set<Integer> hours = parseField(parts[1], 0, 23, "hour");
        Set<Integer> daysOfMonth = parseField(parts[2], 1, 31, "day-of-month");
        Set<Integer> months = parseField(parts[3], 1, 12, "month");
        Set<Integer> daysOfWeek = parseField(parts[4], 0, 6, "day-of-week");

        return new CronExpression(expression, minutes, hours, daysOfMonth, months, daysOfWeek);
    }

    /**
     * 判断给定时间是否匹配此 cron 表达式。
     */
    public boolean matches(LocalDateTime dateTime) {
        int minute = dateTime.getMinute();
        int hour = dateTime.getHour();
        int dayOfMonth = dateTime.getDayOfMonth();
        int month = dateTime.getMonthValue();
        // java DayOfWeek: 1=Monday..7=Sunday, cron: 0=Sunday..6=Saturday
        int dayOfWeek = dateTime.getDayOfWeek().getValue() % 7;

        return minutes.contains(minute)
                && hours.contains(hour)
                && daysOfMonth.contains(dayOfMonth)
                && months.contains(month)
                && daysOfWeek.contains(dayOfWeek);
    }

    /**
     * 计算从 {@code from}（不含）开始的下一次匹配时间。
     * 最多向前搜索 4 年，避免无限循环。
     *
     * @return 下一次匹配时间，若找不到则返回 empty
     */
    public Optional<LocalDateTime> nextFireTime(LocalDateTime from) {
        LocalDateTime candidate = from.plusMinutes(1).withSecond(0).withNano(0);
        LocalDateTime limit = from.plusYears(4);

        while (candidate.isBefore(limit)) {
            if (matches(candidate)) {
                return Optional.of(candidate);
            }
            candidate = candidate.plusMinutes(1);
        }
        return Optional.empty();
    }

    public String getExpression() { return expression; }

    @Override
    public String toString() {
        return "CronExpression{" + expression + "}";
    }

    // --- 内部解析 ---

    private static Set<Integer> parseField(String field, int min, int max, String fieldName) {
        Set<Integer> values = new TreeSet<>();

        for (String part : field.split(",")) {
            part = part.trim();
            if (part.contains("/")) {
                parseStep(part, min, max, fieldName, values);
            } else if (part.contains("-")) {
                parseRange(part, min, max, fieldName, values);
            } else if ("*".equals(part)) {
                for (int i = min; i <= max; i++) values.add(i);
            } else {
                int value = parseIntChecked(part, min, max, fieldName);
                values.add(value);
            }
        }

        if (values.isEmpty()) {
            throw new IllegalArgumentException("Empty value set for field '" + fieldName + "': " + field);
        }
        return Collections.unmodifiableSet(values);
    }

    private static void parseStep(String part, int min, int max, String fieldName, Set<Integer> values) {
        String[] split = part.split("/", 2);
        int step = parseIntChecked(split[1], 1, max, fieldName + " step");

        int rangeStart = min;
        int rangeEnd = max;
        if (!"*".equals(split[0])) {
            if (split[0].contains("-")) {
                String[] rangeParts = split[0].split("-", 2);
                rangeStart = parseIntChecked(rangeParts[0], min, max, fieldName);
                rangeEnd = parseIntChecked(rangeParts[1], min, max, fieldName);
            } else {
                rangeStart = parseIntChecked(split[0], min, max, fieldName);
            }
        }

        for (int i = rangeStart; i <= rangeEnd; i += step) {
            values.add(i);
        }
    }

    private static void parseRange(String part, int min, int max, String fieldName, Set<Integer> values) {
        String[] rangeParts = part.split("-", 2);
        int start = parseIntChecked(rangeParts[0], min, max, fieldName);
        int end = parseIntChecked(rangeParts[1], min, max, fieldName);
        if (start > end) {
            throw new IllegalArgumentException("Invalid range in field '" + fieldName + "': " + part);
        }
        for (int i = start; i <= end; i++) {
            values.add(i);
        }
    }

    private static int parseIntChecked(String text, int min, int max, String fieldName) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        "Value " + value + " out of range [" + min + "," + max + "] for field '" + fieldName + "'");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number '" + text + "' in field '" + fieldName + "'");
        }
    }
}
