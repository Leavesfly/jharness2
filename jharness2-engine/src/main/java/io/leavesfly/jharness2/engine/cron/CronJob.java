package io.leavesfly.jharness2.engine.cron;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 表示一个已注册的 Cron 定时任务。
 */
public class CronJob {

    public enum Status {
        ACTIVE, PAUSED, CANCELLED
    }

    private final String jobId;
    private final String name;
    private final CronExpression cronExpression;
    private final CronTaskHandler handler;
    private final Instant createdAt;
    private final AtomicLong executionCount = new AtomicLong(0);

    private volatile Status status;
    private volatile Instant lastFireTime;
    private volatile Instant nextFireTime;

    public CronJob(String jobId, String name, CronExpression cronExpression, CronTaskHandler handler) {
        this.jobId = jobId;
        this.name = name;
        this.cronExpression = cronExpression;
        this.handler = handler;
        this.createdAt = Instant.now();
        this.status = Status.ACTIVE;
    }

    public String getJobId() { return jobId; }
    public String getName() { return name; }
    public CronExpression getCronExpression() { return cronExpression; }
    public CronTaskHandler getHandler() { return handler; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public Instant getLastFireTime() { return lastFireTime; }
    public Instant getNextFireTime() { return nextFireTime; }
    public long getExecutionCount() { return executionCount.get(); }

    public void setStatus(Status status) { this.status = status; }
    public void setLastFireTime(Instant lastFireTime) { this.lastFireTime = lastFireTime; }
    public void setNextFireTime(Instant nextFireTime) { this.nextFireTime = nextFireTime; }
    public long incrementExecutionCount() { return executionCount.incrementAndGet(); }

    @Override
    public String toString() {
        return "CronJob{id='" + jobId + "', name='" + name + "', cron='" + cronExpression.getExpression()
                + "', status=" + status + ", executions=" + executionCount.get() + "}";
    }
}
