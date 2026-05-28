package io.leavesfly.jharness2.storage.workspace;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OSS 工作空间存储配置属性。
 */
@ConfigurationProperties(prefix = "jharness2.workspace.oss")
public class OssWorkspaceConfig {

    private String endpoint = "";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String bucketName = "";
    private String keyPrefix = "workspaces/";
    private String localCacheDir = "./data/workspace-cache";
    private int syncIntervalSeconds = 30;
    private boolean deleteLocalCacheOnRelease = false;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public String getLocalCacheDir() { return localCacheDir; }
    public void setLocalCacheDir(String localCacheDir) { this.localCacheDir = localCacheDir; }
    public int getSyncIntervalSeconds() { return syncIntervalSeconds; }
    public void setSyncIntervalSeconds(int syncIntervalSeconds) { this.syncIntervalSeconds = syncIntervalSeconds; }
    public boolean isDeleteLocalCacheOnRelease() { return deleteLocalCacheOnRelease; }
    public void setDeleteLocalCacheOnRelease(boolean deleteLocalCacheOnRelease) { this.deleteLocalCacheOnRelease = deleteLocalCacheOnRelease; }
}
