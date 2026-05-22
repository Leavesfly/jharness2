package io.leavesfly.jharness2.engine.cron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cron 调度器 - 提供类似 OpenClaw 的 cron 定时任务能力。
 * <p>
 * 使用一个后台轮询线程每分钟检查一次所有已注册的 CronJob，
 * 当 cron 表达式匹配当前时间时触发对应的 CronTaskHandler。
 * 任务在独立的线程池中执行，避免阻塞调度线程。
 */
public class CronScheduler {

    private static final Logger logger = LoggerFactory.getLogger(CronScheduler.class);

    private final Map<String, CronJob> jobs = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService taskExecutor;
    private final ZoneId zoneId;

    private volatile ScheduledExecutorService scheduler;
    private volatile CronEventListener eventListener;

    public CronScheduler() {
        this(ZoneId.systemDefault());
    }

    public CronScheduler(ZoneId zoneId) {
        this.zoneId = zoneId;
        this.taskExecutor = Executors.newFixedThreadPool(10, r -> {
            Thread thread = new Thread(r, "jharness2-cron-worker-" + UUID.randomUUID().toString().substring(0, 4));
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 注册一个 cron 任务。
     *
     * @param name           任务名称
     * @param cronExpression cron 表达式（5 段格式）
     * @param handler        任务处理器
     * @return 注册后的 CronJob
     */
    public CronJob register(String name, String cronExpression, CronTaskHandler handler) {
        CronExpression parsed = CronExpression.parse(cronExpression);
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        CronJob job = new CronJob(jobId, name, parsed, handler);

        // 计算下一次触发时间
        LocalDateTime now = LocalDateTime.now(zoneId);
        parsed.nextFireTime(now).ifPresent(next ->
                job.setNextFireTime(next.atZone(zoneId).toInstant()));

        jobs.put(jobId, job);
        logger.info("Registered cron job: {}", job);
        return job;
    }

    /** 启动调度器 */
    public synchronized void start() {
        if (running.get()) {
            logger.warn("Cron scheduler is already running");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "jharness2-cron-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        // 计算到下一分钟整点的延迟，确保在每分钟的第 0 秒触发检查
        long nowMs = System.currentTimeMillis();
        long nextMinuteMs = ((nowMs / 60_000) + 1) * 60_000;
        long initialDelayMs = nextMinuteMs - nowMs;

        scheduler.scheduleAtFixedRate(this::tick, initialDelayMs, 60_000, TimeUnit.MILLISECONDS);

        running.set(true);
        logger.info("Cron scheduler started, zone={}", zoneId);
    }

    /** 停止调度器 */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }
        logger.info("Cron scheduler stopped, {} jobs registered", jobs.size());
    }

    /** 完全关闭调度器及任务执行线程池 */
    public void shutdown() {
        stop();
        taskExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            taskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** 移除指定任务 */
    public boolean removeJob(String jobId) {
        CronJob job = jobs.remove(jobId);
        if (job != null) {
            job.setStatus(CronJob.Status.CANCELLED);
            logger.info("Removed cron job: {}", jobId);
            return true;
        }
        return false;
    }

    /** 暂停指定任务 */
    public boolean pauseJob(String jobId) {
        CronJob job = jobs.get(jobId);
        if (job != null && job.getStatus() == CronJob.Status.ACTIVE) {
            job.setStatus(CronJob.Status.PAUSED);
            return true;
        }
        return false;
    }

    /** 恢复指定任务 */
    public boolean resumeJob(String jobId) {
        CronJob job = jobs.get(jobId);
        if (job != null && job.getStatus() == CronJob.Status.PAUSED) {
            job.setStatus(CronJob.Status.ACTIVE);
            return true;
        }
        return false;
    }

    /** 手动触发指定任务（不影响正常调度周期） */
    public boolean triggerNow(String jobId) {
        CronJob job = jobs.get(jobId);
        if (job == null) return false;
        taskExecutor.submit(() -> fireJob(job));
        return true;
    }

    public Optional<CronJob> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public List<CronJob> listJobs() {
        return new ArrayList<>(jobs.values());
    }

    public boolean isRunning() { return running.get(); }

    public void setEventListener(CronEventListener eventListener) {
        this.eventListener = eventListener;
    }

    // --- 内部调度逻辑 ---

    private void tick() {
        LocalDateTime now = LocalDateTime.now(zoneId);
        for (CronJob job : jobs.values()) {
            if (job.getStatus() != CronJob.Status.ACTIVE) continue;

            if (job.getCronExpression().matches(now)) {
                taskExecutor.submit(() -> fireJob(job));
            }

            // 更新下一次触发时间
            job.getCronExpression().nextFireTime(now).ifPresent(next ->
                    job.setNextFireTime(next.atZone(zoneId).toInstant()));
        }
    }

    private void fireJob(CronJob job) {
        Instant fireTime = Instant.now();
        long execNum = job.incrementExecutionCount();
        job.setLastFireTime(fireTime);

        CronTriggerContext context = new CronTriggerContext(
                job.getJobId(), job.getName(), job.getCronExpression().getExpression(),
                fireTime, execNum);

        logger.debug("Firing cron job: {}", context);

        // 通知外部监听器
        CronEventListener listener = this.eventListener;
        if (listener != null) {
            try {
                listener.onCronTriggered(job, context);
            } catch (Exception e) {
                logger.warn("Cron event listener failed for job '{}': {}", job.getJobId(), e.getMessage());
            }
        }

        // 执行任务
        try {
            job.getHandler().execute(context);
        } catch (Exception e) {
            logger.error("Cron job '{}' execution failed: {}", job.getName(), e.getMessage(), e);
        }
    }

    /**
     * 外部事件监听器接口，可用于 Hook 集成。
     */
    @FunctionalInterface
    public interface CronEventListener {
        void onCronTriggered(CronJob job, CronTriggerContext context);
    }
}
