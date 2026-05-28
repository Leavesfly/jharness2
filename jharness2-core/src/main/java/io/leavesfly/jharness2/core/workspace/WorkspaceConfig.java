package io.leavesfly.jharness2.core.workspace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Workspace 增强配置。
 */
@Component
@ConfigurationProperties(prefix = "jharness2.workspace")
public class WorkspaceConfig {

    /** 是否启用 workspace 隔离检查 */
    private boolean isolationEnabled = true;

    /** 单个 workspace 最大容量（MB） */
    private long maxSizeMb = 500;

    /** workspace 清理阈值（超过此比例触发清理，0.0-1.0） */
    private double cleanupThreshold = 0.9;

    /** 模板目录路径 */
    private String templateDirectory = "";

    /** workspace 过期时间（天），超过则可被清理 */
    private int expirationDays = 30;

    public boolean isIsolationEnabled() { return isolationEnabled; }
    public void setIsolationEnabled(boolean isolationEnabled) { this.isolationEnabled = isolationEnabled; }
    public long getMaxSizeMb() { return maxSizeMb; }
    public void setMaxSizeMb(long maxSizeMb) { this.maxSizeMb = maxSizeMb; }
    public double getCleanupThreshold() { return cleanupThreshold; }
    public void setCleanupThreshold(double cleanupThreshold) { this.cleanupThreshold = cleanupThreshold; }
    public String getTemplateDirectory() { return templateDirectory; }
    public void setTemplateDirectory(String templateDirectory) { this.templateDirectory = templateDirectory; }
    public int getExpirationDays() { return expirationDays; }
    public void setExpirationDays(int expirationDays) { this.expirationDays = expirationDays; }
}
