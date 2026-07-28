package io.leavesfly.jharness2.core.distributed;

import io.leavesfly.jharness2.core.EngineInstance;
import io.leavesfly.jharness2.core.UserEngineRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 引擎迁移服务 —— 节点下线前主动将活跃引擎迁移到其他节点。
 * <p>
 * 迁移流程：
 * 1. 停止接受新请求（标记节点为 draining）
 * 2. 等待活跃请求完成
 * 3. 将所有引擎状态同步到 Redis
 * 4. 释放所有引擎的所有权（其他节点可接管）
 * 5. 关闭本地引擎实例
 */
public class EngineMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(EngineMigrationService.class);

    private final DistributedEngineRegistry registry;
    private final EngineStateStore stateStore;
    private final Duration drainTimeout;

    private volatile boolean draining = false;

    public EngineMigrationService(DistributedEngineRegistry registry,
                                  EngineStateStore stateStore,
                                  Duration drainTimeout) {
        this.registry = registry;
        this.stateStore = stateStore;
        this.drainTimeout = drainTimeout;
    }

    /**
     * 是否正在排空中（不接受新请求）。
     */
    public boolean isDraining() {
        return draining;
    }

    /**
     * 开始排空流程：标记为 draining，等待活跃请求完成，同步状态，释放所有权。
     *
     * @return 完成 Future（所有引擎迁移完毕后 resolve）
     */
    public CompletableFuture<MigrationReport> drain() {
        if (draining) {
            return CompletableFuture.completedFuture(MigrationReport.empty());
        }
        draining = true;
        logger.info("Node drain initiated, stopping acceptance of new requests");

        return CompletableFuture.supplyAsync(() -> {
            int before = (int) registry.activeEngineCount();
            // 等活跃请求完成 -> 写回最新状态 -> 让渡所有权，使其他节点可接管
            List<String> migrated = registry.releaseAllForMigration();
            int failed = Math.max(0, before - migrated.size());
            List<String> failedEngines = failed > 0
                    ? List.of(failed + " engine(s) failed to migrate, see logs")
                    : List.of();

            logger.info("Drain completed: migrated={}, failed={}", migrated.size(), failed);
            return new MigrationReport(migrated, failedEngines);
        });
    }

    /**
     * 取消排空状态（回滚）。
     */
    public void cancelDrain() {
        draining = false;
        logger.info("Node drain cancelled, resuming normal operation");
    }

    /**
     * 迁移报告。
     */
    public static class MigrationReport {
        private final List<String> migratedEngines;
        private final List<String> failedEngines;

        public MigrationReport(List<String> migratedEngines, List<String> failedEngines) {
            this.migratedEngines = migratedEngines;
            this.failedEngines = failedEngines;
        }

        public static MigrationReport empty() {
            return new MigrationReport(List.of(), List.of());
        }

        public List<String> getMigratedEngines() { return migratedEngines; }
        public List<String> getFailedEngines() { return failedEngines; }
        public int totalCount() { return migratedEngines.size() + failedEngines.size(); }
        public boolean hasFailures() { return !failedEngines.isEmpty(); }
    }
}
