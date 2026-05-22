package io.leavesfly.jharness2.engine.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jharness2.engine.skill.SkillDefinition;
import io.leavesfly.jharness2.engine.skill.SkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * 插件加载器：发现插件目录、解析 plugin.json、加载各类资源。
 */
public class PluginLoader {

    private static final Logger logger = LoggerFactory.getLogger(PluginLoader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 发现指定目录下的所有插件路径（每个子目录视为一个插件）。
     */
    public static List<Path> discoverPluginPaths(Path pluginsDir) {
        List<Path> paths = new ArrayList<>();
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) return paths;
        try (Stream<Path> stream = Files.list(pluginsDir)) {
            stream.filter(Files::isDirectory).forEach(paths::add);
        } catch (IOException e) {
            logger.warn("Failed to scan plugins directory: {}", pluginsDir, e);
        }
        return paths;
    }

    /**
     * 加载所有插件。
     */
    public static List<LoadedPlugin> loadAll(Path pluginsDir, Map<String, Boolean> enabledPlugins) {
        List<LoadedPlugin> plugins = new ArrayList<>();
        List<Path> pluginPaths = discoverPluginPaths(pluginsDir);

        for (Path pluginPath : pluginPaths) {
            LoadedPlugin plugin = loadPlugin(pluginPath, enabledPlugins);
            if (plugin != null) {
                plugins.add(plugin);
            }
        }
        logger.info("Loaded {} plugins from {}", plugins.size(), pluginsDir);
        return plugins;
    }

    /**
     * 加载单个插件。
     */
    public static LoadedPlugin loadPlugin(Path pluginPath, Map<String, Boolean> enabledPlugins) {
        PluginManifest manifest = findManifest(pluginPath);
        if (manifest == null) {
            logger.debug("No plugin.json found in: {}", pluginPath);
            return null;
        }

        boolean enabled = enabledPlugins != null
                ? enabledPlugins.getOrDefault(manifest.getName(), manifest.isEnabledByDefault())
                : manifest.isEnabledByDefault();

        LoadedPlugin plugin = new LoadedPlugin(manifest, pluginPath, enabled);
        if (!enabled) {
            logger.debug("Plugin disabled: {}", manifest.getName());
            return plugin;
        }

        plugin.setSkills(loadSkillsFromDir(pluginPath, manifest.getSkillsDir(), "skills"));
        plugin.setCommandPrompts(loadSkillsFromDir(pluginPath, manifest.getCommandsDir(), "commands"));
        plugin.setAgentDefs(loadSkillsFromDir(pluginPath, manifest.getAgentsDir(), "agents"));
        plugin.setHooks(loadHooks(pluginPath, manifest));
        plugin.setMcpServers(loadMcpConfig(pluginPath, manifest));

        logger.info("Loaded plugin: {} v{} (skills={}, hooks={}, commands={}, agents={}, mcp={})",
                manifest.getName(), manifest.getVersion(),
                plugin.getSkills().size(), plugin.getHooks().size(),
                plugin.getCommandPrompts().size(), plugin.getAgentDefs().size(),
                plugin.getMcpServers().size());
        return plugin;
    }

    private static PluginManifest findManifest(Path pluginPath) {
        Path manifestFile = pluginPath.resolve("plugin.json");
        if (!Files.exists(manifestFile)) {
            manifestFile = pluginPath.resolve(".jharness-plugin/plugin.json");
        }
        if (!Files.exists(manifestFile)) return null;

        try {
            return MAPPER.readValue(manifestFile.toFile(), PluginManifest.class);
        } catch (IOException e) {
            logger.error("Failed to parse plugin manifest: {}", manifestFile, e);
            return null;
        }
    }

    private static List<SkillDefinition> loadSkillsFromDir(Path pluginPath, String customDir, String defaultDir) {
        List<SkillDefinition> result = new ArrayList<>();
        SkillRegistry tempRegistry = new SkillRegistry();

        Path defaultPath = pluginPath.resolve(defaultDir);
        if (Files.isDirectory(defaultPath)) {
            tempRegistry.loadFromDirectory(defaultPath);
        }

        if (customDir != null && !customDir.isBlank()) {
            Path customPath = pluginPath.resolve(customDir);
            if (Files.isDirectory(customPath) && !customPath.normalize().equals(defaultPath.normalize())) {
                tempRegistry.loadFromDirectory(customPath);
            }
        }

        result.addAll(tempRegistry.getAll());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> loadHooks(Path pluginPath, PluginManifest manifest) {
        Map<String, List<String>> hooks = new HashMap<>();
        String hooksFile = manifest.getHooksFile();
        if (hooksFile == null) hooksFile = "hooks.json";

        Path hooksPath = pluginPath.resolve(hooksFile);
        if (!Files.exists(hooksPath)) return hooks;

        try {
            Map<String, Object> data = MAPPER.readValue(hooksPath.toFile(), Map.class);
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (entry.getValue() instanceof List<?> list) {
                    List<String> commands = new ArrayList<>();
                    for (Object item : list) {
                        commands.add(item.toString());
                    }
                    hooks.put(entry.getKey(), commands);
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to load plugin hooks: {}", hooksPath, e);
        }
        return hooks;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadMcpConfig(Path pluginPath, PluginManifest manifest) {
        String mcpFile = manifest.getMcpFile();
        if (mcpFile == null) mcpFile = "mcp.json";

        Path mcpPath = pluginPath.resolve(mcpFile);
        if (!Files.exists(mcpPath)) return new HashMap<>();

        try {
            return MAPPER.readValue(mcpPath.toFile(), Map.class);
        } catch (IOException e) {
            logger.warn("Failed to load plugin MCP config: {}", mcpPath, e);
            return new HashMap<>();
        }
    }
}
