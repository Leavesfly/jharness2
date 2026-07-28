package io.leavesfly.jharness2.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "jharness2.engine")
public class EngineConfig {

    private String defaultModel = "qwen3.5:4b";
    private String defaultBaseUrl = "http://localhost:11434/v1";
    private String defaultApiKey = "ollama";
    private int maxTokens = 4096;
    private int maxTurns = 12;
    private int connectTimeoutSeconds = 30;
    private int readTimeoutSeconds = 300;
    private int writeTimeoutSeconds = 30;
    private int maxEnginesPerUser = 5;
    private int maxTotalEngines = 200;
    private int engineIdleTimeoutMinutes = 30;
    // 默认命令黑名单：纵深防御的一环，不能替代 OS 级沙箱隔离
    private List<String> deniedCommandPatterns = new ArrayList<>(List.of(
            "rm -rf /*", "rm -rf ~*", "rm -fr /*", "rm -fr ~*",
            "sudo *", "sudo", "su *", "su",
            "shutdown*", "reboot*", "halt*", "poweroff*",
            "mkfs*", "dd if=*", "mkswap*", "fdisk*",
            "kill -9 1", "kill 1", "killall*", "pkill*",
            "chmod -R 777 /*", "chown -R * /*",
            ":(){*", "nc -l*", "curl * | sh*", "curl * | bash*", "wget * | sh*", "wget * | bash*"
    ));

    // 分布式引擎状态外置配置
    private Distributed distributed = new Distributed();

    // 内置工具开关
    private Tools tools = new Tools();

    public static class Tools {
        /**
         * 是否开启 bash 工具。
         * <p>
         * bash 直接在宿主机以应用进程身份执行，命令黑名单与路径校验仅为纵深防御。
         * 多用户部署且无 OS 级沙箱（容器/独立系统用户）时建议置为 false。
         */
        private boolean bashEnabled = true;

        public boolean isBashEnabled() { return bashEnabled; }
        public void setBashEnabled(boolean bashEnabled) { this.bashEnabled = bashEnabled; }
    }

    public static class Distributed {
        private boolean enabled = false;
        private String nodeId = "";
        private int stateSyncIntervalSeconds = 10;
        private int ownershipLeaseSeconds = 60;
        private int stateTtlMinutes = 35;
        /**
         * 本节点对其他节点可达的地址（如 http://10.0.0.1:8080）。
         * 配置后开启节点间请求转发：非属主节点收到会话请求时代理到属主节点；
         * 留空则不注册地址、不参与转发（退回 409 + 会话亲和路由方案）。
         */
        private String advertiseAddress = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public int getStateSyncIntervalSeconds() { return stateSyncIntervalSeconds; }
        public void setStateSyncIntervalSeconds(int stateSyncIntervalSeconds) { this.stateSyncIntervalSeconds = stateSyncIntervalSeconds; }
        public int getOwnershipLeaseSeconds() { return ownershipLeaseSeconds; }
        public void setOwnershipLeaseSeconds(int ownershipLeaseSeconds) { this.ownershipLeaseSeconds = ownershipLeaseSeconds; }
        public int getStateTtlMinutes() { return stateTtlMinutes; }
        public void setStateTtlMinutes(int stateTtlMinutes) { this.stateTtlMinutes = stateTtlMinutes; }
        public String getAdvertiseAddress() { return advertiseAddress; }
        public void setAdvertiseAddress(String advertiseAddress) { this.advertiseAddress = advertiseAddress; }
    }

    // all getters and setters
    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public String getDefaultBaseUrl() { return defaultBaseUrl; }
    public void setDefaultBaseUrl(String defaultBaseUrl) { this.defaultBaseUrl = defaultBaseUrl; }
    public String getDefaultApiKey() { return defaultApiKey; }
    public void setDefaultApiKey(String defaultApiKey) { this.defaultApiKey = defaultApiKey; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public int getMaxTurns() { return maxTurns; }
    public void setMaxTurns(int maxTurns) { this.maxTurns = maxTurns; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    public int getWriteTimeoutSeconds() { return writeTimeoutSeconds; }
    public void setWriteTimeoutSeconds(int writeTimeoutSeconds) { this.writeTimeoutSeconds = writeTimeoutSeconds; }
    public int getMaxEnginesPerUser() { return maxEnginesPerUser; }
    public void setMaxEnginesPerUser(int maxEnginesPerUser) { this.maxEnginesPerUser = maxEnginesPerUser; }
    public int getMaxTotalEngines() { return maxTotalEngines; }
    public void setMaxTotalEngines(int maxTotalEngines) { this.maxTotalEngines = maxTotalEngines; }
    public int getEngineIdleTimeoutMinutes() { return engineIdleTimeoutMinutes; }
    public void setEngineIdleTimeoutMinutes(int engineIdleTimeoutMinutes) { this.engineIdleTimeoutMinutes = engineIdleTimeoutMinutes; }
    public List<String> getDeniedCommandPatterns() { return deniedCommandPatterns; }
    public void setDeniedCommandPatterns(List<String> deniedCommandPatterns) { this.deniedCommandPatterns = deniedCommandPatterns; }
    public Distributed getDistributed() { return distributed; }
    public void setDistributed(Distributed distributed) { this.distributed = distributed; }
    public Tools getTools() { return tools; }
    public void setTools(Tools tools) { this.tools = tools; }
}
