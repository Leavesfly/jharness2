package io.leavesfly.jharness2.engine.plugin;

import io.leavesfly.jharness2.engine.skill.SkillDefinition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 已加载的插件实体，包含清单和从插件目录中解析出的各类资源。
 */
public class LoadedPlugin {

    private final PluginManifest manifest;
    private final Path pluginPath;
    private final boolean enabled;

    private List<SkillDefinition> skills = new ArrayList<>();
    private List<SkillDefinition> commandPrompts = new ArrayList<>();
    private List<SkillDefinition> agentDefs = new ArrayList<>();
    private Map<String, List<String>> hooks = new HashMap<>();
    private Map<String, Object> mcpServers = new HashMap<>();

    public LoadedPlugin(PluginManifest manifest, Path pluginPath, boolean enabled) {
        this.manifest = manifest;
        this.pluginPath = pluginPath;
        this.enabled = enabled;
    }

    public PluginManifest getManifest() { return manifest; }
    public Path getPluginPath() { return pluginPath; }
    public boolean isEnabled() { return enabled; }

    public List<SkillDefinition> getSkills() { return skills; }
    public void setSkills(List<SkillDefinition> skills) { this.skills = skills; }

    public List<SkillDefinition> getCommandPrompts() { return commandPrompts; }
    public void setCommandPrompts(List<SkillDefinition> commandPrompts) { this.commandPrompts = commandPrompts; }

    public List<SkillDefinition> getAgentDefs() { return agentDefs; }
    public void setAgentDefs(List<SkillDefinition> agentDefs) { this.agentDefs = agentDefs; }

    public Map<String, List<String>> getHooks() { return hooks; }
    public void setHooks(Map<String, List<String>> hooks) { this.hooks = hooks; }

    public Map<String, Object> getMcpServers() { return mcpServers; }
    public void setMcpServers(Map<String, Object> mcpServers) { this.mcpServers = mcpServers; }
}
