package io.leavesfly.jharness2.web.controller;

import io.leavesfly.jharness2.core.EngineConfig;
import io.leavesfly.jharness2.core.UserEngineRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统状态与管理接口。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final UserEngineRegistry engineRegistry;
    private final EngineConfig engineConfig;

    public SystemController(UserEngineRegistry engineRegistry, EngineConfig engineConfig) {
        this.engineRegistry = engineRegistry;
        this.engineConfig = engineConfig;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(Authentication auth) {
        String username = auth.getName();
        return ResponseEntity.ok(Map.of(
                "activeEngines", engineRegistry.activeEngineCount(),
                "userEngines", engineRegistry.userEngineCount(username),
                "maxEnginesPerUser", engineConfig.getMaxEnginesPerUser(),
                "maxTotalEngines", engineConfig.getMaxTotalEngines(),
                "defaultModel", engineConfig.getDefaultModel()
        ));
    }

    @GetMapping("/config")
    public ResponseEntity<?> config() {
        return ResponseEntity.ok(Map.of(
                "defaultModel", engineConfig.getDefaultModel(),
                "maxTokens", engineConfig.getMaxTokens(),
                "maxTurns", engineConfig.getMaxTurns(),
                "maxEnginesPerUser", engineConfig.getMaxEnginesPerUser()
        ));
    }
}
