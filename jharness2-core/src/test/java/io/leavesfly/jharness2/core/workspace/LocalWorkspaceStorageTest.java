package io.leavesfly.jharness2.core.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalWorkspaceStorageTest {

    @TempDir
    Path tempDir;

    private LocalWorkspaceStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalWorkspaceStorage(tempDir.toString());
    }

    @Test
    void ensureUserWorkspaceShouldCreateDirectory() {
        Path workspace = storage.ensureUserWorkspace("alice");
        assertTrue(Files.isDirectory(workspace));
        assertEquals(tempDir.resolve("alice"), workspace);
    }

    @Test
    void ensureUserWorkspaceShouldReturnSamePathOnMultipleCalls() {
        Path first = storage.ensureUserWorkspace("bob");
        Path second = storage.ensureUserWorkspace("bob");
        assertEquals(first, second);
    }

    @Test
    void ensureUserWorkspaceShouldSanitizeUserId() {
        Path workspace = storage.ensureUserWorkspace("user/../evil");
        String dirName = workspace.getFileName().toString();
        // '/' is replaced with '_', preventing path traversal
        assertFalse(dirName.contains("/"));
        // The resulting path must stay within the workspace root
        assertTrue(workspace.startsWith(tempDir));
        assertTrue(Files.isDirectory(workspace));
    }

    @Test
    void syncToRemoteShouldBeNoOp() {
        storage.ensureUserWorkspace("alice");
        assertDoesNotThrow(() -> storage.syncToRemote("alice"));
    }

    @Test
    void syncFromRemoteShouldBeNoOp() {
        assertDoesNotThrow(() -> storage.syncFromRemote("alice"));
    }

    @Test
    void releaseShouldBeNoOp() {
        storage.ensureUserWorkspace("alice");
        storage.release("alice", true);
        // Local storage never deletes workspace
        assertTrue(Files.isDirectory(tempDir.resolve("alice")));
    }

    @Test
    void getWorkspaceRootShouldReturnConfiguredPath() {
        assertEquals(tempDir, storage.getWorkspaceRoot());
    }
}
