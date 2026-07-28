package io.leavesfly.jharness2.engine.policy.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多用户场景下的路径隔离回归测试。
 * <p>
 * 覆盖两个曾经失效的点：
 * 1. {@code workspace/**=allow} + {@code /**=deny} 组合必须允许 workspace 内的路径；
 * 2. shell 命令中的绝对路径/穿越路径必须被拦截，避免读写其他用户的 workspace。
 */
class PermissionCheckerPathIsolationTest {

    private static final String WORKSPACE = "/data/workspaces/alice";

    private PermissionChecker checker;

    @BeforeEach
    void setUp() {
        checker = new PermissionChecker(PermissionMode.DEFAULT);
        checker.addPathRule(WORKSPACE, true);
        checker.addPathRule(WORKSPACE + "/**", true);
        checker.addPathRule("/**", false);
        checker.setCommandBaseDir(Path.of(WORKSPACE));
    }

    @Test
    void shouldAllowPathsInsideOwnWorkspace() {
        assertTrue(checker.isPathAllowed(Path.of(WORKSPACE)));
        assertTrue(checker.isPathAllowed(Path.of(WORKSPACE, "notes.md")));
        assertTrue(checker.isPathAllowed(Path.of(WORKSPACE, "src/main/App.java")));
    }

    @Test
    void shouldDenyPathsOutsideOwnWorkspace() {
        assertFalse(checker.isPathAllowed(Path.of("/data/workspaces/bob/secret.txt")));
        assertFalse(checker.isPathAllowed(Path.of("/etc/passwd")));
    }

    @Test
    void shouldAllowNormalCommandsWithRelativePaths() {
        assertTrue(checker.isCommandAllowed("ls -la"));
        assertTrue(checker.isCommandAllowed("cat notes.md"));
        assertTrue(checker.isCommandAllowed("cat ./src/App.java"));
        assertTrue(checker.isCommandAllowed("grep -r foo src/"));
    }

    @Test
    void shouldDenyCommandReadingOtherUserWorkspace() {
        assertFalse(checker.isCommandAllowed("cat ../bob/secret.txt"));
        assertFalse(checker.isCommandAllowed("cp ../bob/data.db ."));
        assertFalse(checker.isCommandAllowed("ls .."));
    }

    @Test
    void shouldDenyCommandReadingAbsoluteSystemPath() {
        assertFalse(checker.isCommandAllowed("cat /etc/passwd"));
        assertFalse(checker.isCommandAllowed("cat /data/jharness2-db.mv.db"));
        assertFalse(checker.isCommandAllowed("echo hi && cat /etc/shadow"));
    }

    @Test
    void shouldDenyHomeDirectoryExpansion() {
        assertFalse(checker.isCommandAllowed("cat ~/.ssh/id_rsa"));
        assertFalse(checker.isCommandAllowed("cat $HOME/.aws/credentials"));
    }

    @Test
    void shouldNotMisjudgeUrlsAndRegexAsPaths() {
        // URL 与 sed 正则包含斜杠，但不应被当成越界路径而误杀
        assertTrue(checker.isCommandAllowed("curl https://example.com/api"));
        assertTrue(checker.isCommandAllowed("sed -i 's/foo/bar/' notes.md"));
    }

    @Test
    void permissiveModeShouldSkipPathChecks() {
        PermissionChecker permissive = new PermissionChecker(PermissionMode.PERMISSIVE);
        permissive.addPathRule("/**", false);
        permissive.setCommandBaseDir(Path.of(WORKSPACE));
        assertTrue(permissive.isCommandAllowed("cat /etc/passwd"));
        assertTrue(permissive.isPathAllowed(Path.of("/etc/passwd")));
    }
}
