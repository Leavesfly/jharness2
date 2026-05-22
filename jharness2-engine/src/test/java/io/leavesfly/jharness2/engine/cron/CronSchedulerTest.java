package io.leavesfly.jharness2.engine.cron;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CronSchedulerTest {

    private CronScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    void shouldRegisterAndListJobs() {
        scheduler = new CronScheduler();
        CronJob job = scheduler.register("test-job", "*/5 * * * *", ctx -> {});

        assertNotNull(job.getJobId());
        assertEquals("test-job", job.getName());
        assertEquals(CronJob.Status.ACTIVE, job.getStatus());
        assertEquals(1, scheduler.listJobs().size());
    }

    @Test
    void shouldRemoveJob() {
        scheduler = new CronScheduler();
        CronJob job = scheduler.register("to-remove", "0 * * * *", ctx -> {});

        assertTrue(scheduler.removeJob(job.getJobId()));
        assertEquals(CronJob.Status.CANCELLED, job.getStatus());
        assertTrue(scheduler.listJobs().isEmpty());
    }

    @Test
    void shouldPauseAndResumeJob() {
        scheduler = new CronScheduler();
        CronJob job = scheduler.register("pausable", "0 * * * *", ctx -> {});

        assertTrue(scheduler.pauseJob(job.getJobId()));
        assertEquals(CronJob.Status.PAUSED, job.getStatus());

        assertTrue(scheduler.resumeJob(job.getJobId()));
        assertEquals(CronJob.Status.ACTIVE, job.getStatus());
    }

    @Test
    void shouldTriggerJobManually() throws InterruptedException {
        scheduler = new CronScheduler();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CronTriggerContext> captured = new AtomicReference<>();

        CronJob job = scheduler.register("manual-trigger", "0 0 1 1 *", ctx -> {
            captured.set(ctx);
            latch.countDown();
        });

        assertTrue(scheduler.triggerNow(job.getJobId()));
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Manual trigger should fire");

        CronTriggerContext ctx = captured.get();
        assertNotNull(ctx);
        assertEquals(job.getJobId(), ctx.getJobId());
        assertEquals("manual-trigger", ctx.getJobName());
        assertEquals(1, ctx.getExecutionNumber());
        assertEquals(1, job.getExecutionCount());
    }

    @Test
    void shouldNotifyEventListener() throws InterruptedException {
        scheduler = new CronScheduler();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CronJob> firedJob = new AtomicReference<>();

        scheduler.setEventListener((job, ctx) -> {
            firedJob.set(job);
            latch.countDown();
        });

        CronJob job = scheduler.register("listener-test", "0 0 1 1 *", ctx -> {});
        scheduler.triggerNow(job.getJobId());

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(job.getJobId(), firedJob.get().getJobId());
    }

    @Test
    void shouldHandleTaskException() throws InterruptedException {
        scheduler = new CronScheduler();

        CountDownLatch latch = new CountDownLatch(1);

        CronJob job = scheduler.register("error-job", "0 0 1 1 *", ctx -> {
            latch.countDown();
            throw new RuntimeException("boom");
        });

        scheduler.triggerNow(job.getJobId());
        assertTrue(latch.await(2, TimeUnit.SECONDS), "Job should still fire despite exception");
        assertEquals(1, job.getExecutionCount());
    }

    @Test
    void shouldStartAndStopCleanly() {
        scheduler = new CronScheduler();
        scheduler.register("start-stop-test", "*/1 * * * *", ctx -> {});

        scheduler.start();
        assertTrue(scheduler.isRunning());

        scheduler.stop();
        assertFalse(scheduler.isRunning());
    }

    @Test
    void shouldReturnFalseForNonexistentJob() {
        scheduler = new CronScheduler();
        assertFalse(scheduler.removeJob("nonexistent"));
        assertFalse(scheduler.pauseJob("nonexistent"));
        assertFalse(scheduler.resumeJob("nonexistent"));
        assertFalse(scheduler.triggerNow("nonexistent"));
    }

    @Test
    void shouldGetJobById() {
        scheduler = new CronScheduler();
        CronJob job = scheduler.register("get-test", "0 9 * * *", ctx -> {});

        assertTrue(scheduler.getJob(job.getJobId()).isPresent());
        assertFalse(scheduler.getJob("nonexistent").isPresent());
    }
}
