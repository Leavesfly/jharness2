package io.leavesfly.jharness2.core.workspace;

import io.leavesfly.jharness2.core.UserEngineRegistry;
import io.leavesfly.jharness2.core.spi.WorkspaceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 工作空间存储自动配置。
 * <p>
 * - 默认（storage-type=local 或未配置）：使用本地文件系统
 * - storage-type=oss：使用 OSS + 本地缓存
 */
@Configuration
public class WorkspaceStorageAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceStorageAutoConfiguration.class);

    /**
     * OSS 模式配置。
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jharness2.workspace", name = "storage-type", havingValue = "oss")
    @EnableConfigurationProperties(OssWorkspaceConfig.class)
    static class OssStorageConfiguration {

        @Bean
        public OssClient ossClient(OssWorkspaceConfig config) {
            logger.info("Creating AliyunOssClient: endpoint={}, bucket={}",
                    config.getEndpoint(), config.getBucketName());
            return new AliyunOssClient(
                    config.getEndpoint(),
                    config.getAccessKeyId(),
                    config.getAccessKeySecret(),
                    config.getBucketName());
        }

        @Bean
        public WorkspaceStorage ossWorkspaceStorage(OssClient ossClient, OssWorkspaceConfig config) {
            logger.info("Using OssWorkspaceStorage (OSS + local cache)");
            return new OssWorkspaceStorage(ossClient, config);
        }
    }

    /**
     * 本地模式（默认）。
     */
    @Bean
    @ConditionalOnMissingBean(WorkspaceStorage.class)
    public WorkspaceStorage localWorkspaceStorage(
            @Value("${jharness2.workspace.root:./data/workspaces}") String rootPath) {
        logger.info("Using LocalWorkspaceStorage (local filesystem)");
        return new LocalWorkspaceStorage(rootPath);
    }

    /**
     * OSS 模式下的定时同步调度器。
     * 定期将所有活跃用户的 workspace 变更上传到 OSS，防止异常退出丢数据。
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jharness2.workspace", name = "storage-type", havingValue = "oss")
    static class OssSyncScheduler {

        private final WorkspaceStorage workspaceStorage;
        private final UserEngineRegistry engineRegistry;

        OssSyncScheduler(WorkspaceStorage workspaceStorage,
                         UserEngineRegistry engineRegistry) {
            this.workspaceStorage = workspaceStorage;
            this.engineRegistry = engineRegistry;
        }

        @Scheduled(fixedDelayString = "${jharness2.workspace.oss.sync-interval-seconds:30}000")
        public void syncActiveWorkspaces() {
            if (!(workspaceStorage instanceof OssWorkspaceStorage ossStorage)) return;
            // 仅同步当前节点上有活跃引擎的用户
            // OssWorkspaceStorage 内部维护了 initializedUsers 集合
            ossStorage.syncAllInitializedUsers();
        }
    }
}
