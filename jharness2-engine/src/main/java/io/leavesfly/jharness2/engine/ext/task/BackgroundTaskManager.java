package io.leavesfly.jharness2.engine.ext.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class BackgroundTaskManager {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundTaskManager.class);
    private final Map<String, BackgroundTask> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final Path outputDir;
    private volatile TaskLifecycleListener lifecycleListener;

    public BackgroundTaskManager(Path outputDir) {
        this.outputDir = outputDir;
        this.executor = Executors.newFixedThreadPool(20, r -> {
            Thread t = new Thread(r, "jharness2-bg-task-" + UUID.randomUUID().toString().substring(0, 4));
            t.setDaemon(true);
            return t;
        });
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            logger.warn("Failed to create task output dir: {}", outputDir);
        }
    }

    /**
     * 设置任务生命周期监听器（可选），由上层模块注入以实现持久化等扩展。
     */
    public void setLifecycleListener(TaskLifecycleListener listener) {
        this.lifecycleListener = listener;
    }

    public BackgroundTask submit(String description, String command, Path workDir) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        BackgroundTask task = new BackgroundTask(taskId, description, command);
        tasks.put(taskId, task);
        notifyCreated(task);

        executor.submit(() -> executeTask(task, workDir));
        logger.info("Submitted background task: id={}, command={}", taskId, command);
        return task;
    }

    private void executeTask(BackgroundTask task, Path workDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", task.getCommand());
            if (workDir != null) pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            task.setProcess(process);

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            task.setOutput(output.toString());
            task.setCompletedAt(Instant.now());
            task.setStatus(exitCode == 0 ? BackgroundTask.Status.COMPLETED : BackgroundTask.Status.FAILED);
            notifyStatusChanged(task);

            // 输出落盘
            Path outputFile = outputDir.resolve(task.getTaskId() + ".log");
            Files.writeString(outputFile, output.toString());

            logger.info("Task {} completed with exit code {}", task.getTaskId(), exitCode);
        } catch (Exception e) {
            task.setStatus(BackgroundTask.Status.FAILED);
            task.setOutput("Error: " + e.getMessage());
            task.setCompletedAt(Instant.now());
            notifyStatusChanged(task);
            logger.error("Task {} failed: {}", task.getTaskId(), e.getMessage());
        }
    }

    public Optional<BackgroundTask> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    public List<BackgroundTask> listTasks() {
        return new ArrayList<>(tasks.values());
    }

    public boolean cancelTask(String taskId) {
        BackgroundTask task = tasks.get(taskId);
        if (task == null || task.getStatus() != BackgroundTask.Status.RUNNING) return false;
        Process process = task.getProcess();
        if (process != null) {
            process.destroy();
            task.setStatus(BackgroundTask.Status.CANCELLED);
            task.setCompletedAt(Instant.now());
            notifyStatusChanged(task);
            return true;
        }
        return false;
    }

    public void shutdown() {
        tasks.values().stream()
                .filter(t -> t.getStatus() == BackgroundTask.Status.RUNNING)
                .forEach(t -> cancelTask(t.getTaskId()));
        executor.shutdown();
    }

    private void notifyCreated(BackgroundTask task) {
        TaskLifecycleListener listener = this.lifecycleListener;
        if (listener != null) {
            try {
                listener.onTaskCreated(task);
            } catch (Exception e) {
                logger.warn("TaskLifecycleListener.onTaskCreated failed for task {}: {}", task.getTaskId(), e.getMessage());
            }
        }
    }

    private void notifyStatusChanged(BackgroundTask task) {
        TaskLifecycleListener listener = this.lifecycleListener;
        if (listener != null) {
            try {
                listener.onTaskStatusChanged(task);
            } catch (Exception e) {
                logger.warn("TaskLifecycleListener.onTaskStatusChanged failed for task {}: {}", task.getTaskId(), e.getMessage());
            }
        }
    }
}
