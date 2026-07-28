package io.leavesfly.jharness2.engine.policy.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionCheckerTest {

    private PermissionChecker checker;

    @BeforeEach
    void setUp() {
        checker = new PermissionChecker(PermissionMode.DEFAULT);
        checker.addDeniedCommand("sudo *");
        checker.addDeniedCommand("sudo");
        checker.addDeniedCommand("rm -rf /*");
        checker.addDeniedCommand(":(){*");
    }

    @Test
    void shouldAllowNormalCommand() {
        assertTrue(checker.isCommandAllowed("ls -la"));
        assertTrue(checker.isCommandAllowed("echo hello && cat file.txt"));
    }

    @Test
    void shouldDenyDirectMatch() {
        assertFalse(checker.isCommandAllowed("sudo rm -rf /tmp"));
        assertFalse(checker.isCommandAllowed("rm -rf /etc"));
    }

    @Test
    void shouldDenyChainedCommandBypass() {
        // 前缀拼接不应绕过前缀型黑名单
        assertFalse(checker.isCommandAllowed("echo ok && sudo reboot"));
        assertFalse(checker.isCommandAllowed("ls; sudo cat /etc/shadow"));
        assertFalse(checker.isCommandAllowed("true || sudo id"));
        assertFalse(checker.isCommandAllowed("cat x | sudo tee /etc/hosts"));
    }

    @Test
    void shouldHandleRegexMetaCharactersInPattern() {
        // fork bomb 模式包含正则元字符，不应导致匹配失败或异常
        assertFalse(checker.isCommandAllowed(":(){ :|:& };:"));
    }

    @Test
    void permissiveModeShouldAllowEverything() {
        PermissionChecker permissive = new PermissionChecker(PermissionMode.PERMISSIVE);
        permissive.addDeniedCommand("sudo *");
        assertTrue(permissive.isCommandAllowed("sudo reboot"));
    }
}
