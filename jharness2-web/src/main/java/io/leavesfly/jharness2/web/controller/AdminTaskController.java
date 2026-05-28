package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.core.spi.TaskState;
import io.leavesfly.jharness2.core.spi.TaskStateStore;
import io.leavesfly.jharness2.core.spi.TaskStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 后台任务管理接口 —— 查看任务状态、取消任务。
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
    public ResponseEntity<?> getTask(@PathVariable String taskId) {
        return taskStateStore.findById(taskId)
                .map(task -> ResponseEntity.ok((Object) task))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<?> cancelTask(@PathVariable String taskId) {
        taskStateStore.updateStatus(taskId, TaskStatus.CANCELLED, "Cancelled by user");
        return ResponseEntity.ok(Map.of("status", "cancelled", "taskId", taskId));
    }

    @GetMapping("/timed-out")
    public ResponseEntity<?> timedOutTasks() {
        List<TaskState> tasks = taskStateStore.findTimedOutTasks(Instant.now());
        return ResponseEntity.ok(Map.of("count", tasks.size(), "tasks", tasks));
    }
}
