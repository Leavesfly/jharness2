package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.engine.message.ConversationMessage;
import io.leavesfly.jharness2.engine.EngineContext;
import io.leavesfly.jharness2.engine.llm.OpenAiClient;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.ext.skill.SkillRegistry;
import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolRegistry;
import io.leavesfly.jharness2.engine.tool.builtin.file.FileReadTool;
import io.leavesfly.jharness2.engine.tool.builtin.file.FileWriteTool;
import io.leavesfly.jharness2.engine.tool.builtin.file.GrepTool;
import io.leavesfly.jharness2.engine.tool.builtin.file.GlobTool;
import io.leavesfly.jharness2.engine.tool.builtin.shell.BashTool;
import io.leavesfly.jharness2.engine.tool.builtin.meta.SkillTool;
import io.leavesfly.jharness2.engine.ext.skill.SkillLoader;
import io.leavesfly.jharness2.core.engine.EngineCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * 引擎工厂 —— 负责基础引擎构建（LLM + 工具 + 技能），
 * 然后委托 {@link EngineCustomizer} 列表完成各子系统的定制初始化。
 * <p>
 * 遵循开闭原则：新增子系统只需实现 EngineCustomizer 并注册为 Bean，无需修改本类。
 */
@Component
public class DefaultEngineFactory implements EngineFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEngineFactory.class);

    private static final List<BaseTool<?>> BUILTIN_TOOLS = List.of(
            new FileReadTool(),
            new FileWriteTool(),
            new GrepTool(),
            new GlobTool(),
            new BashTool()
    );

    private final EngineConfig engineConfig;
    private final WorkspaceInitializer workspaceInitializer;
    private final List<EngineCustomizer> customizers;

    public DefaultEngineFactory(EngineConfig engineConfig,
                                WorkspaceInitializer workspaceInitializer,
                                @Autowired(required = false) List<EngineCustomizer> customizers) {
        this.engineConfig = engineConfig;
        this.workspaceInitializer = workspaceInitializer;
        this.customizers = customizers != null
                ? customizers.stream().sorted(Comparator.comparingInt(EngineCustomizer::getOrder)).toList()
                : List.of();
        logger.info("DefaultEngineFactory initialized with {} customizers", this.customizers.size());
    }

    @Override
    public EngineInstance restore(UserContext context, List<ConversationMessage> messages,
                                  long inputTokens, long outputTokens) {
        EngineInstance instance = create(context);
        QueryEngine engine = instance.getEngine();
        engine.loadMessages(messages);
        engine.getCostTracker().restore(inputTokens, outputTokens);
        logger.info("Restored engine for user={}, session={}, messages={}, tokens={}+{}",
                context.getUserId(), context.getSessionId(), messages.size(), inputTokens, outputTokens);
        return instance;
    }

    @Override
    public EngineInstance create(UserContext context) {
        String baseUrl = context.getBaseUrl() != null ? context.getBaseUrl() : engineConfig.getDefaultBaseUrl();
        String apiKey = context.getApiKey() != null ? context.getApiKey() : engineConfig.getDefaultApiKey();
        String model = context.getModel() != null ? context.getModel() : engineConfig.getDefaultModel();

        Path workspace = context.getWorkspace() != null
                ? context.getWorkspace()
                : workspaceInitializer.ensureUserWorkspace(context.getUserId());

        // 1. 基础构建：LLM 客户端
        OpenAiClient llmClient = new OpenAiClient(
                baseUrl, apiKey, model,
                engineConfig.getMaxTokens(),
                engineConfig.getConnectTimeoutSeconds(),
                engineConfig.getReadTimeoutSeconds(),
                engineConfig.getWriteTimeoutSeconds());

        // 2. 工具注册表 + 内置工具
        ToolRegistry toolRegistry = new ToolRegistry();
        BUILTIN_TOOLS.forEach(toolRegistry::register);

        // 3. 技能系统
        SkillRegistry skillRegistry = SkillLoader.loadAll(workspace);
        toolRegistry.register(new SkillTool(skillRegistry));

        // 4. 构建 system prompt
        String systemPrompt = buildSystemPrompt(context.getUserId(), workspace, skillRegistry);

        // 5. 创建 QueryEngine + EngineContext
        QueryEngine engine = new QueryEngine(llmClient, toolRegistry, systemPrompt, engineConfig.getMaxTurns());
        engine.setWorkingDirectory(workspace);
        engine.getCostTracker().setModelName(model);

        EngineContext engineContext = new EngineContext();
        engineContext.setSkillRegistry(skillRegistry);
        engine.setEngineContext(engineContext);

        // 6. 委托 Customizer 链完成各子系统定制（权限、压缩、插件、Agent、持久化等）
        for (EngineCustomizer customizer : customizers) {
            try {
                customizer.customize(engine, context, workspace);
            } catch (Exception e) {
                logger.warn("EngineCustomizer {} failed: {}",
                        customizer.getClass().getSimpleName(), e.getMessage());
            }
        }

        logger.info("Created engine for user={}, session={}, model={}, workspace={}, customizers={}, tools={}",
                context.getUserId(), context.getSessionId(), model, workspace,
                customizers.size(), toolRegistry.size());

        return new EngineInstance(engine, context);
    }

    private String buildSystemPrompt(String userId, Path workspace, SkillRegistry skillRegistry) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 JHarness AI 助手，正在为用户 ").append(userId).append(" 服务。\n");
        prompt.append("你可以使用工具来帮助用户完成各种任务。\n");
        prompt.append("用户的工作空间路径为：").append(workspace.toAbsolutePath()).append("\n");
        prompt.append("你支持 Sub-Agent 协作、MCP 协议工具调用和后台任务执行。\n");

        String skillSection = skillRegistry.buildSystemPromptSection();
        if (!skillSection.isEmpty()) {
            prompt.append(skillSection);
        }

        return prompt.toString();
    }
}
