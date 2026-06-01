package io.leavesfly.jharness2.engine.ext.evolution.strategy;

import io.leavesfly.jharness2.engine.ext.evolution.EvolutionConfig;
import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StrategyEvolverTest {

    @TempDir
    Path tempDir;

    private LlmClient mockLlmClient;
    private DirectiveStore directiveStore;
    private StrategyEvolver evolver;
    private EvolutionConfig config;

    @BeforeEach
    void setUp() {
        mockLlmClient = mock(LlmClient.class);
        directiveStore = new DirectiveStore(tempDir);
        config = new EvolutionConfig();
        config.setStrategyEnabled(true);
        config.setEvaluationWindow(3); // 小窗口方便测试
        config.setMaxDirectiveLength(500);

        evolver = new StrategyEvolver(mockLlmClient, directiveStore, config);
    }

    @Test
    void evolveGeneratesDirectiveFromMetrics() {
        String mockDirectiveJson = """
                ```json
                {
                  "promptAddendum": "优先使用 file_read 确认文件内容后再修改",
                  "suggestedMaxTurns": 10,
                  "avoidPatterns": ["盲目执行 bash 命令", "不检查就修改文件"],
                  "reasoning": "工具错误率偏高，应先读后写"
                }
                ```
                """;
        when(mockLlmClient.chatStream(any(), any(), any()))
                .thenReturn(new LlmResponse(mockDirectiveJson, null, 200, 300));

        // 添加足够的 metrics 触发进化
        for (int i = 0; i < 3; i++) {
            SessionMetrics metrics = new SessionMetrics("session-" + i, 12);
            metrics.setTurnsUsed(8);
            metrics.setToolSequence(List.of("bash", "file_read", "bash"));
            metrics.setToolErrorCount(2);
            metrics.complete(i < 2, 500, 300); // 2/3 完成
            evolver.recordMetrics(metrics);
        }

        // 等待异步进化
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        Optional<EvolutionDirective> directive = directiveStore.getCurrent();
        assertTrue(directive.isPresent());
        assertEquals(1, directive.get().getVersion());
        assertTrue(directive.get().getPromptAddendum().contains("file_read"));
        assertFalse(directive.get().getAvoidPatterns().isEmpty());
    }

    @Test
    void getActiveDirectiveSectionReturnsFormattedText() {
        EvolutionDirective directive = EvolutionDirective.create(
                1, "先读取文件再修改，避免盲改",
                null, 10, List.of("不确认就删除", "跳过错误检查"));
        directiveStore.save(directive);

        String section = evolver.getActiveDirectiveSection();
        assertFalse(section.isEmpty());
        assertTrue(section.contains("策略优化指令 (v1)"));
        assertTrue(section.contains("先读取文件再修改"));
        assertTrue(section.contains("不确认就删除"));
    }

    @Test
    void getActiveDirectiveSectionReturnsEmptyWhenNoDirective() {
        String section = evolver.getActiveDirectiveSection();
        assertEquals("", section);
    }

    @Test
    void rollbackRevertsToLowerVersion() {
        // 保存 v1
        EvolutionDirective v1 = EvolutionDirective.create(1, "v1 策略", null, 12, List.of());
        directiveStore.save(v1);

        // 保存 v2
        EvolutionDirective v2 = EvolutionDirective.create(2, "v2 策略", null, 10, List.of("avoid"));
        directiveStore.save(v2);

        assertEquals(2, directiveStore.getLatestVersion());

        // 回滚到 v1
        boolean result = evolver.rollback();
        assertTrue(result);

        Optional<EvolutionDirective> current = directiveStore.getCurrent();
        assertTrue(current.isPresent());
        assertEquals(1, current.get().getVersion());
        assertEquals("v1 策略", current.get().getPromptAddendum());
    }

    @Test
    void directiveStorePersistsAcrossInstances() {
        EvolutionDirective directive = EvolutionDirective.create(
                1, "持久化测试指令", null, 12, List.of("pattern1"));
        directiveStore.save(directive);

        // 创建新的 store 实例（模拟重启）
        DirectiveStore newStore = new DirectiveStore(tempDir);
        Optional<EvolutionDirective> loaded = newStore.getCurrent();

        assertTrue(loaded.isPresent());
        assertEquals("持久化测试指令", loaded.get().getPromptAddendum());
        assertEquals(1, loaded.get().getVersion());
    }

    @Test
    void directiveStoreListsVersions() {
        directiveStore.save(EvolutionDirective.create(1, "v1", null, 12, List.of()));
        directiveStore.save(EvolutionDirective.create(2, "v2", null, 12, List.of()));
        directiveStore.save(EvolutionDirective.create(3, "v3", null, 12, List.of()));

        List<Integer> versions = directiveStore.listVersions();
        assertEquals(List.of(1, 2, 3), versions);
    }
}
