package io.leavesfly.jharness2.core.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceIsolationCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldAllowPathWithinUserWorkspace() {
        WorkspaceIsolationChecker checker = new WorkspaceIsolationChecker(tempDir, true);
        Path target = tempDir.resolve("user1/project/file.txt");
        assertTrue(checker.isPathAllowed("user1", target));
    }

    @Test
    void shouldDenyPathOutsideUserWorkspace() {
        WorkspaceIsolationChecker checker = new WorkspaceIsolationChecker(tempDir, true);
        Path target = tempDir.resolve("user2/project/file.txt");
        assertFalse(checker.isPathAllowed("user1", target));
    }

    @Test
    void shouldAllowAnyPathWhenDisabled() {
        WorkspaceIsolationChecker checker = new WorkspaceIsolationChecker(tempDir, false);
        Path target = Path.of("/etc/passwd");
        assertTrue(checker.isPathAllowed("user1", target));
    }

    @Test
    void shouldDetectTraversalRisk() {
        WorkspaceIsolationChecker checker = new WorkspaceIsolationChecker(tempDir, true);
        assertTrue(checker.hasTraversalRisk("../etc/passwd"));
        assertTrue(checker.hasTraversalRisk("~/secret"));
        assertFalse(checker.hasTraversalRisk("project/src/main.java"));
    }

    @Test
    void resolveAndValidateShouldRejectTraversal() {
        WorkspaceIsolationChecker checker = new WorkspaceIsolationChecker(tempDir, true);
        assertThrows(SecurityException.class,
                () -> checker.resolveAndValidate("user1", "../user2/secret.txt"));
    }

    @Test
    void resolveAndValidateShouldAcceptValidPath() {
        WorkspaceIsolationChecker checker = new WorkspaceIsolationChecker(tempDir, true);
        Path result = checker.resolveAndValidate("user1", "project/file.txt");
        assertTrue(result.toString().contains("user1"));
    }
}
