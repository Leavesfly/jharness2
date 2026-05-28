package io.leavesfly.jharness2.storage.checkpoint;

import io.leavesfly.jharness2.core.EngineFactory;
import io.leavesfly.jharness2.core.checkpoint.*;
import io.leavesfly.jharness2.core.session.SessionResumeService;
import io.leavesfly.jharness2.core.spi.CheckpointStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Checkpoint 子域自动配置。
 */
@Configuration
@ConditionalOnProperty(prefix = "jharness2.checkpoint", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CheckpointAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CheckpointAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(CheckpointStore.class)
    public CheckpointStore inMemoryCheckpointStore(CheckpointConfig config) {
        logger.info("Using InMemoryCheckpointStore (maxPerSession={})", config.getMaxCheckpointsPerSession());
        return new InMemoryCheckpointStore(config.getMaxCheckpointsPerSession());
    }

    @Bean
    @ConditionalOnBean(CheckpointStore.class)
    public SessionCheckpointService sessionCheckpointService(CheckpointStore store, CheckpointConfig config) {
        return new SessionCheckpointService(store, config);
    }

    @Bean
    @ConditionalOnBean(SessionCheckpointService.class)
    public SessionResumeService sessionResumeService(SessionCheckpointService checkpointService,
                                                     EngineFactory engineFactory) {
        return new SessionResumeService(checkpointService, engineFactory);
    }
}
