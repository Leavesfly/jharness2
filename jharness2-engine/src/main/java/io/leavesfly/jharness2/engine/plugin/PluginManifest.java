package io.leavesfly.jharness2.engine.plugin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 插件清单模型，对应 plugin.json 文件。
 * 声明插件的元信息和各资源目录/文件位置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginManifest {

    private String name;
    private String version;
    private String description;
    private boolean enabledByDefault = true;

    private String skillsDir;
    private String commandsDir;
    private String agentsDir;
    private String hooksFile;
    private String mcpFile;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isEnabledByDefault() { return enabledByDefault; }
    public void setEnabledByDefault(boolean enabledByDefault) { this.enabledByDefault = enabledByDefault; }

    public String getSkillsDir() { return skillsDir; }
    public void setSkillsDir(String skillsDir) { this.skillsDir = skillsDir; }

    public String getCommandsDir() { return commandsDir; }
    public void setCommandsDir(String commandsDir) { this.commandsDir = commandsDir; }

    public String getAgentsDir() { return agentsDir; }
    public void setAgentsDir(String agentsDir) { this.agentsDir = agentsDir; }

    public String getHooksFile() { return hooksFile; }
    public void setHooksFile(String hooksFile) { this.hooksFile = hooksFile; }

    public String getMcpFile() { return mcpFile; }
    public void setMcpFile(String mcpFile) { this.mcpFile = mcpFile; }
}
