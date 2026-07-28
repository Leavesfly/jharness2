package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.core.spi.TaskState;
import io.leavesfly.jharness2.core.spi.TaskStateStore;
import io.leavesfly.jharness2.core.spi.TaskStatus;
import io.leavesfly.jharness2.web.security.AuthzSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 后台任务管理接口 —— 查看任务状态、取消任务。
 * <p>
 * 除管理员外，只能访问自己名下的任务。
 */
@RestController
@RequestMapping("/api/admin/tasks")
public class AdminTaskController {

    private final TaskStateStore taskStateStore;

    public AdminTaskController(TaskStateStore taskStateStore) {
        this.taskStateStore = taskStateStore;
    }

    @GetMapping("/active")
    public ResponseEntity<?> activeTasks(Authentication auth) {
        String userId = auth.getName();
        List<TaskState> tasks = taskStateStore.findActiveTasks(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "count", tasks.size(), "tasks", tasks));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<?> getTask(Authentication auth, @PathVariable String taskId) {
        TaskState task = loadOwnedTask(auth, taskId);
        return ResponseEntity.ok(task);
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<?> cancelTask(Authentication auth, @PathVariable String taskId) {
        loadOwnedTask(auth, taskId);
        taskStateStore.updateStatus(taskId, TaskStatus.CANCELLED, "Cancelled by user");
        return ResponseEntity.ok(Map.of("status", "cancelled", "taskId", taskId));
    }

    /**
     * 全局超时任务视图属于运维数据，仅管理员可见。
     */
    @GetMapping("/timed-out")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> timedOutTasks() {
        List<TaskState> tasks = taskStateStore.findTimedOutTasks(Instant.now());
        return ResponseEntity.ok(Map.of("count", tasks.size(), "tasks", tasks));
    }

    /**
     * 加载任务并校验归属：非管理员访问他人任务时统一返回 404，避免任务 ID 被探测。
     */
    private TaskState loadOwnedTask(Authentication auth, String taskId) {
        TaskState task = taskStateStore.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        if (!AuthzSupport.isAdmin(auth) && !auth.getName().equals(task.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return task;
    }
}
