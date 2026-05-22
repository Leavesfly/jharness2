package io.leavesfly.jharness2.engine.cron;

/**
 * Cron 任务处理器。当 cron 表达式匹配触发时被回调。
 */
@FunctionalInterface
public interface CronTaskHandler {

    /**
     * 执行定时任务。
     *
     * @param context 本次触发的上下文信息
     */
    void execute(CronTriggerContext context);
}
