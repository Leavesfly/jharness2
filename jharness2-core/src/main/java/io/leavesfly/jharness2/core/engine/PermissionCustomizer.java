package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.EngineConfig;
import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.engine.QueryEngine;
import io.leavesfly.jharness2.engine.policy.access.PermissionChecker;
import io.leavesfly.jharness2.engine.policy.access.PermissionMode;

import java.nio.file.Path;

/**
 * 权限系统定制器 —— 配置工作空间路径规则和命令黑名单。
 */
public class PermissionCustomizer implements EngineCustomizer {

    private final EngineConfig engineConfig;

    public PermissionCustomizer(EngineConfig engineConfig) {
        this.engineConfig = engineConfig;
    }

    @Override
    public void customize(QueryEngine engine, UserContext context, Path workspace) {
        PermissionChecker permissionChecker = new PermissionChecker(PermissionMode.DEFAULT);
        String root = workspace.toAbsolutePath().normalize().toString();
        // workspace 目录本身也需放行（如 ls .），否则会被兜底的 /** 拒绝规则命中
        permissionChecker.addPathRule(root, true);
        permissionChecker.addPathRule(root + "/**", true);
        permissionChecker.addPathRule("/**", false);
        for (String pattern : engineConfig.getDeniedCommandPatterns()) {
            permissionChecker.addDeniedCommand(pattern);
        }
        // 使 shell 命令中的路径也受 workspace 隔离约束（防止 cat ../其他用户/...）
        permissionChecker.setCommandBaseDir(workspace);
        engine.setPermissionChecker(permissionChecker);
    }

    @Override
    public int getOrder() { return 100; }
}
