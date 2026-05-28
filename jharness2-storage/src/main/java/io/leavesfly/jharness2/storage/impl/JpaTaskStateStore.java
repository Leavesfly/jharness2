package io.leavesfly.jharness2.storage.impl;

import io.leavesfly.jharness2.core.spi.TaskState;
import io.leavesfly.jharness2.core.spi.TaskStateStore;
import io.leavesfly.jharness2.core.spi.TaskStatus;
import io.leavesfly.jharness2.storage.entity.AgentTaskEntity;
import io.leavesfly.jharness2.storage.repository.AgentTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class JpaTaskStateStore implements TaskStateStore {

    private static final Logger logger = LoggerFactory.getLogger(JpaTaskStateStore.class);
    private final AgentTaskRepository repository;

    public JpaTaskStateStore(AgentTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public void create(TaskState task) {
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setId(task.taskId());
        entity.setUserId(task.userId());
        entity.setSessionId(task.sessionId());
        entity.setParentTaskId(task.parentTaskId());
        entity.setType(task.type());
        entity.setStatus(task.status().name());
        entity.setInput(task.input());
        entity.setOutput(task.output());
        entity.setRetryCount(task.retryCount());
        entity.setMaxRetries(task.maxRetries());
        entity.setCreatedAt(task.createdAt());
        entity.setUpdatedAt(task.updatedAt());
        entity.setTimeoutAt(task.timeoutAt());
        repository.save(entity);
        logger.debug("Task created: id={}, type={}, user={}", task.taskId(), task.type(), task.userId());
    }

    @Override
    public void updateStatus(String taskId, TaskStatus status, String output) {
        repository.updateStatus(taskId, status.name(), output, Instant.now());
        logger.debug("Task status updated: id={}, status={}", taskId, status);
    }

    @Override
    public Optional<TaskState> findById(String taskId) {
        return repository.findById(taskId).map(this::toTaskState);
    }

    @Override
    public List<TaskState> findActiveTasks(String userId) {
        return repository.findActiveTasks(userId).stream()
                .map(this::toTaskState)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskState> findTimedOutTasks(Instant timeoutBefore) {
        return repository.findTimedOut(timeoutBefore).stream()
                .map(this::toTaskState)
                .collect(Collectors.toList());
    }

    @Override
    public void incrementRetry(String taskId) {
        repository.incrementRetryCount(taskId, Instant.now());
        logger.debug("Task retry incremented: id={}", taskId);
    }

    private TaskState toTaskState(AgentTaskEntity entity) {
        return new TaskState(
                entity.getId(),
                entity.getUserId(),
                entity.getSessionId(),
                entity.getParentTaskId(),
                entity.getType(),
                TaskStatus.valueOf(entity.getStatus()),
                entity.getInput(),
                entity.getOutput(),
                entity.getRetryCount(),
                entity.getMaxRetries(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getTimeoutAt()
        );
    }
}
