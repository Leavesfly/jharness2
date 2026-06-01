package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.EngineConfig;
import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.engine.EngineContext;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.ext.evolution.EvolutionConfig;
import io.leavesfly.jharness2.engine.ext.evolution.EvolutionEngine;
import io.leavesfly.jharness2.engine.ext.evolution.toolmaker.CreateToolTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 进化子系统 Customizer —— 在引擎创建时初始化并注册 EvolutionEngine。
 * <p>
 * 执行顺序设为 100（在权限、压缩、插件等基础 Customizer 之后）。
 */
@Component
public class EvolutionCustomizer implements EngineCustomizer {

    private static final Logger logger = LoggerFactory.getLogger(EvolutionCustomizer.class);

    private final EngineConfig engineConfig;

    public EvolutionCustomizer(EngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }

    @Override
    public void customize(QueryEngine engine, UserContext context, Path workspace) {
        EvolutionConfig config = EvolutionConfig.load(workspace);

        // 如果 workspace 没有配置文件，使用系统默认配置
        if (!config.isEnabled()) {
            config = buildDefaultConfig();
        }

        if (!config.isEnabled()) {
            logger.debug("Evolution disabled for user={}", context.getUserId());
            return;
        }

        // 创建 EvolutionEngine
        EvolutionEngine evolutionEngine = new EvolutionEngine(
                engine.getLlmClient(), workspace, config);

        // 注册 Hook 监听
        evolutionEngine.registerHooks(engine.getHookExecutor());

        // 注册到 EngineContext
        EngineContext engineContext = engine.getEngineContext();
        if (engineContext != null) {
            engineContext.registerExtension(EvolutionEngine.class, evolutionEngine);
        }

        // 初始化 ToolMaker（Level 2）并注册 CreateToolTool
        Optional<CreateToolTool> createToolTool = evolutionEngine.initToolMaker(engine.getToolRegistry());
        createToolTool.ifPresent(tool -> engine.getToolRegistry().register(tool));

        // 启动本次会话的 metrics 采集
        String sessionId = context.getSessionId() != null ? context.getSessionId() : "default";
        evolutionEngine.onSessionStart(sessionId, engineConfig.getMaxTurns());

        logger.info("Evolution subsystem initialized for user={}, session={}, experience={}, toolMaker={}, strategy={}",
                context.getUserId(), sessionId,
                config.isExperienceEnabled(), config.isToolMakerEnabled(), config.isStrategyEnabled());
    }

    @Override
    public int getOrder() {
        return 100;
    }

    private EvolutionConfig buildDefaultConfig() {
        EvolutionConfig config = new EvolutionConfig();
        config.setEnabled(true);
        config.setExperienceEnabled(true);
        config.setToolMakerEnabled(false);
        config.setStrategyEnabled(false);
        return config;
    }
}
