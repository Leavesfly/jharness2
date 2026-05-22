package io.leavesfly.jharness2.engine.plugin;

import io.leavesfly.jharness2.engine.hook.HookEvent;
import io.leavesfly.jharness2.engine.hook.HookExecutor;
import io.leavesfly.jharness2.engine.hook.ShellHookHandler;
import io.leavesfly.jharness2.engine.mcp.McpManager;
import io.leavesfly.jharness2.engine.skill.SkillDefinition;
import io.leavesfly.jharness2.engine.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件注册表：管理所有已加载的插件，并提供将插件资源注入引擎子系统的能力。
 */
public class PluginRegistry {

    private static final Logger logger = LoggerFactory.getLogger(PluginRegistry.class);
    private final Map<String, LoadedPlugin> plugins = new ConcurrentHashMap<>();

    /**
     * 从指定目录加载所有插件。
     */
    public void loadFromDirectory(Path pluginsDir, Map<String, Boolean> enabledPlugins) {
        List<LoadedPlugin> loaded = PluginLoader.loadAll(pluginsDir, enabledPlugins);
        for (LoadedPlugin plugin : loaded) {
            plugins.put(plugin.getManifest().getName(), plugin);
        }
    }

    /**
     * 将所有已加载且启用的插件的 skills 注入到 SkillRegistry 中。
     */
    public int injectSkills(SkillRegistry skillRegistry) {
        int count = 0;
        for (LoadedPlugin plugin : plugins.values()) {
            if (!plugin.isEnabled()) continue;
            for (SkillDefinition skill : plugin.getSkills()) {
                skillRegistry.register(skill);
                count++;
            }
            for (SkillDefinition agent : plugin.getAgentDefs()) {
                skillRegistry.register(agent);
                count++;
            }
        }
        return count;
    }

    /**
     * 将插件声明的 MCP 服务器配置注入到 McpManager 中。
     * mcp.json 格式: { "servers": { "name": { "command": [...], "env": {...} } } }
     */
    @SuppressWarnings("unchecked")
    public int injectMcpServers(McpManager mcpManager) {
        int count = 0;
        for (LoadedPlugin plugin : plugins.values()) {
            if (!plugin.isEnabled()) continue;
            Map<String, Object> mcpConfig = plugin.getMcpServers();
            Object serversObj = mcpConfig.get("servers");
            if (!(serversObj instanceof Map<?, ?> servers)) continue;

            for (Map.Entry<?, ?> entry : servers.entrySet()) {
                String serverName = entry.getKey().toString();
                if (!(entry.getValue() instanceof Map<?, ?> serverConfig)) continue;

                Object cmdObj = serverConfig.get("command");
                if (!(cmdObj instanceof List<?> cmdList)) continue;

                List<String> command = new ArrayList<>();
                for (Object item : cmdList) {
                    command.add(item.toString());
                }

                Map<String, String> env = new HashMap<>();
                Object envObj = serverConfig.get("env");
                if (envObj instanceof Map<?, ?> envMap) {
                    for (Map.Entry<?, ?> envEntry : envMap.entrySet()) {
                        env.put(envEntry.getKey().toString(), envEntry.getValue().toString());
                    }
                }

                mcpManager.addServer(serverName, command, env);
                count++;
            }
        }
        return count;
    }

    /**
     * 将插件声明的 hooks 注入到 HookExecutor 中（通过 ShellHookHandler 执行 shell 命令）。
     */
    public int injectHooks(HookExecutor hookExecutor) {
        int count = 0;
        for (LoadedPlugin plugin : plugins.values()) {
            if (!plugin.isEnabled()) continue;
            for (Map.Entry<String, List<String>> entry : plugin.getHooks().entrySet()) {
                HookEvent event = resolveHookEvent(entry.getKey());
                if (event == null) {
                    logger.warn("Plugin '{}' declares unknown hook event: {}", plugin.getManifest().getName(), entry.getKey());
                    continue;
                }
                for (String command : entry.getValue()) {
                    hookExecutor.register(event, new ShellHookHandler(command, plugin.getManifest().getName()));
                    count++;
                }
            }
        }
        if (count > 0) {
            logger.info("Registered {} shell hook handlers from plugins", count);
        }
        return count;
    }

    private HookEvent resolveHookEvent(String eventName) {
        for (HookEvent event : HookEvent.values()) {
            if (event.getValue().equals(eventName) || event.name().equalsIgnoreCase(eventName)) {
                return event;
            }
        }
        return null;
    }

    public Optional<LoadedPlugin> get(String name) {
        return Optional.ofNullable(plugins.get(name));
    }

    public Collection<LoadedPlugin> getAll() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    public int size() {
        return plugins.size();
    }

    public int enabledCount() {
        return (int) plugins.values().stream().filter(LoadedPlugin::isEnabled).count();
    }
}
