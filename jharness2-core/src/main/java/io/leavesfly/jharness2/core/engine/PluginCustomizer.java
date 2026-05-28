package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.engine.EngineContext;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.ext.hook.HookExecutor;
import io.leavesfly.jharness2.engine.ext.mcp.McpManager;
import io.leavesfly.jharness2.engine.ext.plugin.PluginRegistry;
import io.leavesfly.jharness2.engine.ext.skill.SkillRegistry;

import java.nio.file.Path;
import java.util.Map;

/**
 * 插件/MCP/Hook 定制器 —— 加载插件并注入 Hook、MCP 服务器。
 */
public class PluginCustomizer implements EngineCustomizer {

    @Override
    public void customize(QueryEngine engine, UserContext context, Path workspace) {
        EngineContext engineContext = engine.getEngineContext();
        if (engineContext == null) {
            engineContext = new EngineContext();
            engine.setEngineContext(engineContext);
        }

        SkillRegistry skillRegistry = engineContext.getSkillRegistry().orElse(null);

        // 插件系统
        PluginRegistry pluginRegistry = new PluginRegistry();
        Path pluginsDir = workspace.resolve(".jharness/plugins");
        pluginRegistry.loadFromDirectory(pluginsDir, Map.of());
        if (skillRegistry != null) {
            pluginRegistry.injectSkills(skillRegistry);
        }

        // Hook 系统
        HookExecutor hookExecutor = new HookExecutor();
        pluginRegistry.injectHooks(hookExecutor);
        engine.setHookExecutor(hookExecutor);

        // MCP 管理器
        McpManager mcpManager = new McpManager();
        pluginRegistry.injectMcpServers(mcpManager);
        mcpManager.connectAndRegisterAsync(engine.getToolRegistry());
        engineContext.setMcpManager(mcpManager);
    }

    @Override
    public int getOrder() { return 300; }
}
