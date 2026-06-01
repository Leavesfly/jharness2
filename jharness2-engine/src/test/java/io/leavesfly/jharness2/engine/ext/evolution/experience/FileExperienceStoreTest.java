package io.leavesfly.jharness2.engine.ext.evolution.experience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileExperienceStoreTest {

    @TempDir
    Path tempDir;

    private FileExperienceStore store;

    @BeforeEach
    void setUp() {
        store = new FileExperienceStore(tempDir, 10);
    }

    @Test
    void saveAndSearchByKeyword() {
        Experience exp = Experience.create(
                "user1", "文件批量重命名",
                List.of("bash", "glob"),
                "使用 find + rename 命令组合",
                true,
                "先用 glob 确认文件列表再执行重命名更安全",
                List.of("重命名", "文件", "批量"),
                5, 2000L
        );

        store.save(exp);

        List<Experience> results = store.search("user1", "批量重命名文件", 5);
        assertFalse(results.isEmpty());
        assertEquals(exp.getId(), results.get(0).getId());
        assertEquals("文件批量重命名", results.get(0).getTaskPattern());
    }

    @Test
    void searchReturnsEmptyForDifferentUser() {
        Experience exp = Experience.create(
                "user1", "API 开发",
                List.of("file_write"),
                "REST 风格",
                true,
                "注意版本控制",
                List.of("api", "rest", "开发"),
                3, 1000L
        );
        store.save(exp);

        List<Experience> results = store.search("user2", "api 开发", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void getRecentReturnsInReverseChronologicalOrder() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            Experience exp = Experience.create(
                    "user1", "任务" + i,
                    List.of("tool" + i),
                    "策略" + i,
                    true,
                    "教训" + i,
                    List.of("keyword" + i),
                    i + 1, (i + 1) * 500L
            );
            store.save(exp);
            Thread.sleep(10); // 确保时间顺序
        }

        List<Experience> recent = store.getRecent("user1", 2);
        assertEquals(2, recent.size());
        assertEquals("任务2", recent.get(0).getTaskPattern());
        assertEquals("任务1", recent.get(1).getTaskPattern());
    }

    @Test
    void deleteRemovesExperience() {
        Experience exp = Experience.create(
                "user1", "测试删除",
                List.of("bash"),
                "直接删除",
                true,
                "无",
                List.of("删除", "测试"),
                2, 500L
        );
        store.save(exp);
        assertEquals(1, store.count("user1"));

        store.delete(exp.getId());
        assertEquals(0, store.count("user1"));

        List<Experience> results = store.search("user1", "删除", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void evictsOldestWhenExceedingLimit() {
        FileExperienceStore smallStore = new FileExperienceStore(tempDir.resolve("small"), 3);

        for (int i = 0; i < 4; i++) {
            Experience exp = Experience.create(
                    "user1", "任务" + i,
                    List.of("tool"),
                    "策略",
                    true,
                    "教训",
                    List.of("keyword"),
                    1, 100L
            );
            smallStore.save(exp);
        }

        // 应该只保留 3 条（淘汰了最旧的 1 条）
        assertEquals(3, smallStore.count("user1"));
    }

    @Test
    void updateRelevanceScore() {
        Experience exp = Experience.create(
                "user1", "评分测试",
                List.of("tool"),
                "策略",
                true,
                "教训",
                List.of("评分"),
                1, 100L
        );
        store.save(exp);
        assertEquals(0.0f, exp.getRelevanceScore());

        store.updateRelevanceScore(exp.getId(), 1.5f);

        List<Experience> results = store.search("user1", "评分", 1);
        assertFalse(results.isEmpty());
        assertEquals(1.5f, results.get(0).getRelevanceScore(), 0.001f);
    }

    @Test
    void searchPrioritizesSuccessOverFailure() {
        Experience failure = Experience.create(
                "user1", "API 部署失败",
                List.of("bash"),
                "直接部署",
                false,
                "应该先检查配置",
                List.of("api", "部署"),
                5, 3000L
        );
        store.save(failure);

        Experience success = Experience.create(
                "user1", "API 部署成功",
                List.of("bash", "file_read"),
                "先检查配置再部署",
                true,
                "配置检查很重要",
                List.of("api", "部署"),
                3, 1500L
        );
        store.save(success);

        List<Experience> results = store.search("user1", "api 部署", 2);
        assertEquals(2, results.size());
        // 成功的排在前面
        assertTrue(results.get(0).isSuccess());
    }
}
