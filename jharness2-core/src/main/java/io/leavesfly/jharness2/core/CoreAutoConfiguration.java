package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.core.workspace.WorkspaceCapacityChecker;
import io.leavesfly.jharness2.core.workspace.WorkspaceConfig;
import io.leavesfly.jharness2.core.workspace.WorkspaceIsolationChecker;
import io.leavesfly.jharness2.core.workspace.WorkspaceTemplateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Workspace 增强自动配置。
 * <p>
 * 其他子域配置已拆分到 {@code io.leavesfly.jharness2.core.config} 包下：
 * <ul>
 *   <li>{@code EngineCustomizerAutoConfiguration} - 引擎定制器</li>
 *   <li>{@code PipelineAutoConfiguration} - 请求管道拦截器</li>
 *   <li>{@code CheckpointAutoConfiguration} - Checkpoint 子域</li>
 *   <li>{@code QuotaAutoConfiguration} - 配额与限流</li>
 *   <li>{@code MetricsAutoConfiguration} - Metrics 度量</li>
 *   <li>{@code EventAutoConfiguration} - 事件总线</li>
 * </ul>
 */
@Configuration
public class CoreAutoConfiguration {

    @Bean
    public WorkspaceIsolationChecker workspaceIsolationChecker(WorkspaceInitializer initializer,
                                                               WorkspaceConfig workspaceConfig) {
        return new WorkspaceIsolationChecker(
                initializer.getWorkspaceRoot(), workspaceConfig.isIsolationEnabled());
    }

    @Bean
    public WorkspaceCapacityChecker workspaceCapacityChecker(WorkspaceConfig workspaceConfig) {
        return new WorkspaceCapacityChecker(
                workspaceConfig.getMaxSizeMb(), workspaceConfig.getCleanupThreshold());
    }

    @Bean
    @ConditionalOnProperty(prefix = "jharness2.workspace", name = "template-directory")
    public WorkspaceTemplateService workspaceTemplateService(WorkspaceConfig workspaceConfig) {
        return new WorkspaceTemplateService(Path.of(workspaceConfig.getTemplateDirectory()));
    }
}
