package io.leavesfly.jharness2.engine.ext.evolution;

import io.leavesfly.jharness2.engine.ext.evolution.experience.*;
import io.leavesfly.jharness2.engine.ext.evolution.strategy.DirectiveStore;
import io.leavesfly.jharness2.engine.ext.evolution.strategy.MetricsCollector;
import io.leavesfly.jharness2.engine.ext.evolution.strategy.SessionMetrics;
import io.leavesfly.jharness2.engine.ext.evolution.strategy.StrategyEvolver;
import io.leavesfly.jharness2.engine.ext.evolution.toolmaker.*;
import io.leavesfly.jharness2.engine.ext.hook.HookEvent;
import io.leavesfly.jharness2.engine.ext.hook.HookExecutor;
import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 进化引擎统一入口 —— 协调经验记忆、工具自生成、策略进化三个子系统。
 * <p>
 * 通过 {@code EngineContext.registerExtension(EvolutionEngine.class, instance)} 注册，
 * 零 Spring 依赖，可在 engine 层独立使用。
 */
public class EvolutionEngine implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(EvolutionEngine.class);

    private final EvolutionConfig config;
    private final Path workspace;
    private final LlmClient llmClient;

    // Level 1: 经验记忆
    private final ExperienceStore experienceStore;
    private final ExperienceExtractor experienceExtractor;
    private final ExperienceRetriever experienceRetriever;

    // Level 2: 工具自生成
    private volatile ToolMaker toolMaker;
    private volatile ToolPersister toolPersister;

    // Level 3: 策略进化
    private volatile StrategyEvolver strategyEvolver;
    private volatile DirectiveStore directiveStore;

    // Level 2/3: 指标采集（同时服务于经验提取和策略进化）
    private final MetricsCollector metricsCollector;

    public EvolutionEngine(LlmClient llmClient, Path workspace, EvolutionConfig config) {
        this.llmClient = llmClient;
        this.workspace = workspace;
        this.config = config;

        // 初始化 Level 1
        this.experienceStore = new FileExperienceStore(workspace, config.getMaxExperiencesPerUser());
        this.experienceExtractor = new ExperienceExtractor(
                llmClient, experienceStore, config.getMinTurnsToExtract());
        this.experienceRetriever = new ExperienceRetriever(
                experienceStore, config.getMaxExperiencesInPrompt());

        // 初始化 Level 2
        this.toolPersister = new ToolPersister(workspace);

        // 初始化 Level 3
        this.directiveStore = new DirectiveStore(workspace);
        if (config.isStrategyEnabled()) {
            this.strategyEvolver = new StrategyEvolver(llmClient, directiveStore, config);
        }

        // 初始化 MetricsCollector
        this.metricsCollector = new MetricsCollector();

        logger.info("EvolutionEngine initialized: workspace={}, experience={}, toolMaker={}, strategy={}",
                workspace, config.isExperienceEnabled(), config.isToolMakerEnabled(), config.isStrategyEnabled());
    }

    /**
     * 将进化引擎注册到 HookExecutor，监听关键事件。
     */
    public void registerHooks(HookExecutor hookExecutor) {
        if (hookExecutor == null) return;

        // 注册工具调用监听（MetricsCollector）
        hookExecutor.register(HookEvent.POST_TOOL_USE, metricsCollector);

        logger.debug("Evolution hooks registered");
    }

    /**
     * 在会话开始时调用 —— 初始化本次会话的指标采集。
     *
     * @param sessionId 会话 ID
     * @param maxTurns  最大轮次
     * @return 本次会话的 metrics 实例
     */
    public SessionMetrics onSessionStart(String sessionId, int maxTurns) {
        return metricsCollector.startSession(sessionId, maxTurns);
    }

    /**
     * 在会话结束时调用 —— 触发经验提取和指标 flush。
     *
     * @param sessionId    会话 ID
     * @param userId       用户 ID
     * @param messages     完整会话消息
     * @param inputTokens  输入 token 数
     * @param outputTokens 输出 token 数
     */
    public void onSessionEnd(String sessionId, String userId,
                             List<ConversationMessage> messages,
                             long inputTokens, long outputTokens) {
        SessionMetrics metrics = metricsCollector.flush(sessionId);
        if (metrics == null) {
            logger.debug("No metrics found for session={}, skipping experience extraction", sessionId);
            return;
        }

        metrics.complete(true, inputTokens, outputTokens);

        // 异步提取经验
        if (config.isExperienceEnabled() && config.isAutoExtractOnSessionEnd()) {
            experienceExtractor.extract(userId, messages, metrics)
                    .thenAccept(exp -> {
                        if (exp != null) {
                            logger.info("Experience extracted: id={}, session={}", exp.getId(), sessionId);
                        }
                    })
                    .exceptionally(ex -> {
                        logger.warn("Experience extraction failed for session={}: {}", sessionId, ex.getMessage());
                        return null;
                    });
        }

        // 策略进化：记录 metrics 到 buffer（积累到窗口大小时自动触发进化）
        if (strategyEvolver != null) {
            strategyEvolver.recordMetrics(metrics);
        }
    }

    /**
     * 获取当前用户的经验提示段（用于注入 system prompt）。
     *
     * @param userId          用户 ID
     * @param taskDescription 当前任务描述
     * @return 经验文本段（空字符串表示无相关经验）
     */
    public String getExperiencePromptSection(String userId, String taskDescription) {
        if (!config.isExperienceEnabled()) return "";
        return experienceRetriever.buildExperienceSection(userId, taskDescription);
    }

    /**
     * 手动记录一次工具调用到当前会话的 metrics。
     * （供 QueryEngine 在 ReAct 循环中直接调用）
     */
    public void recordToolCall(String sessionId, String toolName, boolean success) {
        SessionMetrics metrics = metricsCollector.getMetrics(sessionId);
        if (metrics != null) {
            metrics.recordToolCall(toolName);
            if (!success) {
                metrics.recordToolError();
            }
        }
    }

    /**
     * 记录当前会话轮次增加。
     */
    public void recordTurnIncrement(String sessionId) {
        SessionMetrics metrics = metricsCollector.getMetrics(sessionId);
        if (metrics != null) {
            metrics.incrementTurns();
        }
    }

    /**
     * 初始化工具自生成子系统（Level 2）。
     * 需要 ToolRegistry 才能注册生成的工具，因此在 EngineCustomizer 中调用。
     *
     * @param toolRegistry 引擎的工具注册表
     * @return 可注册到引擎的 CreateToolTool（如果 toolMaker 启用）
     */
    public Optional<CreateToolTool> initToolMaker(ToolRegistry toolRegistry) {
        if (!config.isToolMakerEnabled()) {
            return Optional.empty();
        }

        ToolValidator validator = new ToolValidator(config.getDeniedImports(), config.getCompileTimeoutSeconds());
        this.toolMaker = new ToolMaker(llmClient, validator, toolPersister, toolRegistry, config.getMaxToolsPerUser());

        // 加载已有的自定义工具
        List<BaseTool<?>> existingTools = toolPersister.loadAll();
        for (BaseTool<?> tool : existingTools) {
            toolRegistry.register(tool);
        }
        if (!existingTools.isEmpty()) {
            logger.info("Loaded {} existing custom tools into registry", existingTools.size());
        }

        return Optional.of(new CreateToolTool(toolMaker));
    }

    // --- Accessors ---

    /**
     * 获取策略进化的 prompt 段（用于注入 system prompt）。
     */
    public String getStrategyDirectiveSection() {
        if (strategyEvolver == null) return "";
        return strategyEvolver.getActiveDirectiveSection();
    }

    // --- Accessors ---

    public EvolutionConfig getConfig() { return config; }
    public ExperienceStore getExperienceStore() { return experienceStore; }
    public ExperienceRetriever getExperienceRetriever() { return experienceRetriever; }
    public MetricsCollector getMetricsCollector() { return metricsCollector; }
    public Optional<ToolMaker> getToolMaker() { return Optional.ofNullable(toolMaker); }
    public ToolPersister getToolPersister() { return toolPersister; }
    public Optional<StrategyEvolver> getStrategyEvolver() { return Optional.ofNullable(strategyEvolver); }
    public DirectiveStore getDirectiveStore() { return directiveStore; }

    @Override
    public void close() {
        logger.debug("EvolutionEngine closed for workspace={}", workspace);
    }
}
