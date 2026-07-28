package io.leavesfly.jharness2.core.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工作区目录命名的隔离性回归测试。
 * <p>
 * 关键不变量：不同 userId 永远不能映射到同一目录，否则等于跨用户工作区互通。
 */
class WorkspacePathNamingTest {

    @Test
    void safeUserIdShouldKeepOriginalName() {
        assertEquals("alice", WorkspacePathNaming.toDirectoryName("alice"));
        assertEquals("user_1", WorkspacePathNaming.toDirectoryName("user_1"));
        assertEquals("a-b.c", WorkspacePathNaming.toDirectoryName("a-b.c"));
    }

    @Test
    void differentUserIdsMustNotCollideAfterSanitization() {
        // "a b" 与 "a_b" 归一化后都会变成 "a_b"，必须靠哈希后缀区分开
        String first = WorkspacePathNaming.toDirectoryName("a b");
        String second = WorkspacePathNaming.toDirectoryName("a_b");
        assertNotEquals(first, second);

        assertNotEquals(WorkspacePathNaming.toDirectoryName("a/b"),
                WorkspacePathNaming.toDirectoryName("a:b"));
    }

    @Test
    void resultShouldContainOnlySafeCharacters() {
        String name = WorkspacePathNaming.toDirectoryName("../../etc/passwd");
        assertFalse(name.contains("/"));
        assertFalse(name.contains(".."));
        assertTrue(name.matches("[A-Za-z0-9._-]+"));
    }

    @Test
    void hiddenAndDotNamesShouldGetSuffix() {
        assertNotEquals(".", WorkspacePathNaming.toDirectoryName("."));
        assertNotEquals("..", WorkspacePathNaming.toDirectoryName(".."));
        assertNotEquals(".ssh", WorkspacePathNaming.toDirectoryName(".ssh"));
    }

    @Test
    void longUserIdShouldBeTruncatedButStillUnique() {
        String a = "u".repeat(200) + "a";
        String b = "u".repeat(200) + "b";
        String nameA = WorkspacePathNaming.toDirectoryName(a);
        String nameB = WorkspacePathNaming.toDirectoryName(b);
        assertTrue(nameA.length() <= 64);
        assertNotEquals(nameA, nameB);
    }

    @Test
    void shouldBeStableAcrossCalls() {
        assertEquals(WorkspacePathNaming.toDirectoryName("a b"),
                WorkspacePathNaming.toDirectoryName("a b"));
    }

    @Test
    void blankUserIdShouldBeRejected() {
        assertThrows(IllegalArgumentException.class, () -> WorkspacePathNaming.toDirectoryName(null));
        assertThrows(IllegalArgumentException.class, () -> WorkspacePathNaming.toDirectoryName("  "));
    }
}
