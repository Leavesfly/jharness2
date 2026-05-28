package io.leavesfly.jharness2.core.workspace;

import io.leavesfly.jharness2.core.spi.WorkspaceStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工作空间存储自动配置（本地模式）。
 * <p>
 * 默认使用本地文件系统。OSS 模式由 storage 模块的
 * {@code OssWorkspaceStorageAutoConfiguration} 提供。
 */
@Configuration
public class WorkspaceStorageAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceStorageAutoConfiguration.class);

    /**
     * 本地模式（默认 fallback）。
     * 当没有其他 WorkspaceStorage Bean 时激活。
     */
    @Bean
    @ConditionalOnMissingBean(WorkspaceStorage.class)
    public WorkspaceStorage localWorkspaceStorage(
            @Value("${jharness2.workspace.root:./data/workspaces}") String rootPath) {
        logger.info("Using LocalWorkspaceStorage (local filesystem)");
        return new LocalWorkspaceStorage(rootPath);
    }
}
