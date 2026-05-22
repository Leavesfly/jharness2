package io.leavesfly.jharness2.engine.cron;

import java.time.Instant;

/**
 * Cron 任务触发时的上下文信息。
 */
public class CronTriggerContext {

    private final String jobId;
    private final String jobName;
    private final String cronExpression;
    private final Instant fireTime;
    private final long executionNumber;

    public CronTriggerContext(String jobId, String jobName, String cronExpression,
                              Instant fireTime, long executionNumber) {
        this.jobId = jobId;
        this.jobName = jobName;
        this.cronExpression = cronExpression;
        this.fireTime = fireTime;
        this.executionNumber = executionNumber;
    }

    public String getJobId() { return jobId; }
    public String getJobName() { return jobName; }
    public String getCronExpression() { return cronExpression; }
    public Instant getFireTime() { return fireTime; }
    public long getExecutionNumber() { return executionNumber; }

    @Override
    public String toString() {
        return "CronTriggerContext{jobId='" + jobId + "', jobName='" + jobName
                + "', cron='" + cronExpression + "', execution=" + executionNumber + "}";
    }
}
