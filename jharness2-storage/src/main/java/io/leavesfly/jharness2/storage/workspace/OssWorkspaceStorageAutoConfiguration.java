package io.leavesfly.jharness2.storage.workspace;

import io.leavesfly.jharness2.core.spi.WorkspaceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * OSS 工作空间存储自动配置。
 * <p>
 * 仅在 jharness2.workspace.storage-type=oss 时激活。
 */
@Configuration
@ConditionalOnProperty(prefix = "jharness2.workspace", name = "storage-type", havingValue = "oss")
@EnableConfigurationProperties(OssWorkspaceConfig.class)
public class OssWorkspaceStorageAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(OssWorkspaceStorageAutoConfiguration.class);

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

    /**
     * OSS 模式下的定时同步调度器。
     * 定期将所有活跃用户的 workspace 变更上传到 OSS，防止异常退出丢数据。
     */
    @Configuration
    @ConditionalOnProperty(prefix = "jharness2.workspace", name = "storage-type", havingValue = "oss")
    static class OssSyncScheduler {

        private final WorkspaceStorage workspaceStorage;

        OssSyncScheduler(WorkspaceStorage workspaceStorage) {
            this.workspaceStorage = workspaceStorage;
        }

        @Scheduled(fixedDelayString = "${jharness2.workspace.oss.sync-interval-seconds:30}000")
        public void syncActiveWorkspaces() {
            if (!(workspaceStorage instanceof OssWorkspaceStorage ossStorage)) return;
            ossStorage.syncAllInitializedUsers();
        }
    }
}
