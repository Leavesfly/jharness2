package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.engine.OpenAiClient;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.agent.AgentOrchestrator;
import io.leavesfly.jharness2.engine.compaction.MessageCompactionService;
import io.leavesfly.jharness2.engine.hook.HookExecutor;
import io.leavesfly.jharness2.engine.mcp.McpManager;
import io.leavesfly.jharness2.engine.permission.PermissionChecker;
import io.leavesfly.jharness2.engine.permission.PermissionMode;
import io.leavesfly.jharness2.engine.plugin.PluginRegistry;
import io.leavesfly.jharness2.engine.skill.SkillRegistry;
import io.leavesfly.jharness2.engine.task.BackgroundTaskManager;
import io.leavesfly.jharness2.engine.tool.ToolRegistry;
import io.leavesfly.jharness2.engine.tool.builtin.file.FileReadTool;
import io.leavesfly.jharness2.engine.tool.builtin.file.FileWriteTool;
import io.leavesfly.jharness2.engine.tool.builtin.file.GrepTool;
import io.leavesfly.jharness2.engine.tool.builtin.file.GlobTool;
import io.leavesfly.jharness2.engine.tool.builtin.shell.BashTool;
import io.leavesfly.jharness2.engine.tool.builtin.meta.SkillTool;
import io.leavesfly.jharness2.engine.tool.builtin.meta.SubAgentTool;
import io.leavesfly.jharness2.engine.skill.SkillLoader;
import io.leavesfly.jharness2.core.spi.SessionPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

@Component
public class DefaultEngineFactory implements EngineFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEngineFactory.class);

    private final EngineConfig engineConfig;
    private final WorkspaceInitializer workspaceInitializer;
    private final SessionPersistenceService sessionStorageService;

    public DefaultEngineFactory(EngineConfig engineConfig,
                                WorkspaceInitializer workspaceInitializer,
                                SessionPersistenceService sessionStorageService) {
        this.engineConfig = engineConfig;
        this.workspaceInitializer = workspaceInitializer;
        this.sessionStorageService = sessionStorageService;
    }

    @Override
    public EngineInstance create(UserContext context) {
        String baseUrl = context.getBaseUrl() != null ? context.getBaseUrl() : engineConfig.getDefaultBaseUrl();
        String apiKey = context.getApiKey() != null ? context.getApiKey() : engineConfig.getDefaultApiKey();
        String model = context.getModel() != null ? context.getModel() : engineConfig.getDefaultModel();

        // 确保用户 workspace 存在
        Path workspace = context.getWorkspace() != null
                ? context.getWorkspace()
                : workspaceInitializer.ensureUserWorkspace(context.getUserId());

        // 1. LLM 客户端
        OpenAiClient llmClient = new OpenAiClient(
                baseUrl, apiKey, model,
                engineConfig.getMaxTokens(),
                engineConfig.getConnectTimeoutSeconds(),
                engineConfig.getReadTimeoutSeconds(),
                engineConfig.getWriteTimeoutSeconds());

        // 2. 工具注册表 - 注册内置工具
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new FileReadTool());
        toolRegistry.register(new FileWriteTool());
        toolRegistry.register(new GrepTool());
        toolRegistry.register(new GlobTool());
        toolRegistry.register(new BashTool());

        // 3. 技能系统 - 三层加载（内置 + 用户 + 项目），并将 SkillTool 注册到工具表
        SkillRegistry skillRegistry = SkillLoader.loadAll(workspace);
        toolRegistry.register(new SkillTool(skillRegistry));

        // 4. 插件系统 - 从 workspace 的 plugins 目录加载插件
        PluginRegistry pluginRegistry = new PluginRegistry();
        Path pluginsDir = workspace.resolve(".jharness/plugins");
        pluginRegistry.loadFromDirectory(pluginsDir, Map.of());
        pluginRegistry.injectSkills(skillRegistry);

        // 5. 构建 system prompt（含技能信息）
        String systemPrompt = buildSystemPrompt(context.getUserId(), workspace, skillRegistry);

        // 5. 创建 QueryEngine
        QueryEngine engine = new QueryEngine(llmClient, toolRegistry, systemPrompt, engineConfig.getMaxTurns());
        engine.setWorkingDirectory(workspace);
        engine.getCostTracker().setModelName(model);

        // 6. 权限系统
        PermissionChecker permissionChecker = new PermissionChecker(PermissionMode.DEFAULT);
        permissionChecker.addPathRule(workspace.toAbsolutePath().toString() + "/**", true);
        permissionChecker.addPathRule("/**", false);
        for (String pattern : engineConfig.getDeniedCommandPatterns()) {
            permissionChecker.addDeniedCommand(pattern);
        }
        engine.setPermissionChecker(permissionChecker);

        // 7. 消息压缩
        MessageCompactionService compactionService = new MessageCompactionService();
        compactionService.withSystemPromptTokens(systemPrompt.length() / 3);
        engine.setCompactionService(compactionService);

        // 8. Sub-Agent 协调器 + 注册为 Tool
        AgentOrchestrator agentOrchestrator = new AgentOrchestrator(llmClient);
        engine.setAgentOrchestrator(agentOrchestrator);
        toolRegistry.register(new SubAgentTool(agentOrchestrator));

        // 9. Skill 注入
        engine.setSkillRegistry(skillRegistry);

        // 10. MCP 管理器 + 插件声明的 MCP 服务器 + 动态工具注册
        McpManager mcpManager = new McpManager();
        pluginRegistry.injectMcpServers(mcpManager);
        mcpManager.connectAll();
        mcpManager.registerToolsTo(toolRegistry);
        engine.setMcpManager(mcpManager);

        // 11. Hook 系统 + 插件声明的 Hooks
        HookExecutor hookExecutor = new HookExecutor();
        pluginRegistry.injectHooks(hookExecutor);
        engine.setHookExecutor(hookExecutor);

        // 12. 后台任务管理
        Path taskOutputDir = workspace.resolve(".jharness/task-output");
        BackgroundTaskManager taskManager = new BackgroundTaskManager(taskOutputDir);
        engine.setBackgroundTaskManager(taskManager);

        // 13. 会话自动保存
        String userId = context.getUserId();
        String sessionId = context.getSessionId();
        engine.setSessionPersister(messages -> {
            try {
                if (messages == null || messages.isEmpty()) return;
                long inputTokens = engine.getCostTracker().getInputTokens();
                long outputTokens = engine.getCostTracker().getOutputTokens();
                sessionStorageService.saveSession(userId, sessionId, model,
                        messages, inputTokens, outputTokens);
            } catch (Exception e) {
                logger.debug("Auto-save session failed (ignored): user={}, session={}, error={}",
                        userId, sessionId, e.getMessage());
            }
        });

        logger.info("Created engine for user={}, session={}, model={}, workspace={}, skills={}, tools={}",
                userId, sessionId, model, workspace, skillRegistry.size(), toolRegistry.size());

        return new EngineInstance(engine, context);
    }

    private String buildSystemPrompt(String userId, Path workspace, SkillRegistry skillRegistry) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 JHarness AI 助手，正在为用户 ").append(userId).append(" 服务。\n");
        prompt.append("你可以使用工具来帮助用户完成各种任务。\n");
        prompt.append("用户的工作空间路径为：").append(workspace.toAbsolutePath()).append("\n");
        prompt.append("你支持 Sub-Agent 协作、MCP 协议工具调用和后台任务执行。\n");

        // 注入技能信息
        String skillSection = skillRegistry.buildSystemPromptSection();
        if (!skillSection.isEmpty()) {
            prompt.append(skillSection);
        }

        return prompt.toString();
    }
}
