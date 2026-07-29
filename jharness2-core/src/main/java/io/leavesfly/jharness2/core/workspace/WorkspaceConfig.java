package io.leavesfly.jharness2.core.workspace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Workspace 增强配置。
 */
@Component
@ConfigurationProperties(prefix = "jharness2.workspace")
public class WorkspaceConfig {

    /** 模板目录路径 */
    private String templateDirectory = "";

    public String getTemplateDirectory() { return templateDirectory; }
    public void setTemplateDirectory(String templateDirectory) { this.templateDirectory = templateDirectory; }
}
