package io.leavesfly.jharness2.engine.ext.evolution;

import io.leavesfly.jharness2.engine.ext.evolution.strategy.SessionMetrics;
import io.leavesfly.jharness2.engine.ext.hook.HookEvent;
import io.leavesfly.jharness2.engine.ext.hook.HookExecutor;
import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EvolutionEngineTest {

    @TempDir
    Path tempDir;

    private LlmClient mockLlmClient;
    private EvolutionEngine evolutionEngine;
    private EvolutionConfig config;

    @BeforeEach
    void setUp() {
        mockLlmClient = mock(LlmClient.class);
        config = new EvolutionConfig();
        config.setEnabled(true);
        config.setExperienceEnabled(true);
        config.setMinTurnsToExtract(2);

        evolutionEngine = new EvolutionEngine(mockLlmClient, tempDir, config);
    }

    @Test
    void sessionMetricsLifecycle() {
        SessionMetrics metrics = evolutionEngine.onSessionStart("session-1", 12);
        assertNotNull(metrics);
        assertEquals("session-1", metrics.getSessionId());
        assertEquals(12, metrics.getMaxTurns());

        evolutionEngine.recordToolCall("session-1", "bash", true);
        evolutionEngine.recordToolCall("session-1", "file_read", true);
        evolutionEngine.recordToolCall("session-1", "bash", false);
        evolutionEngine.recordTurnIncrement("session-1");
        evolutionEngine.recordTurnIncrement("session-1");

        assertEquals(3, metrics.getToolSequence().size());
        assertEquals(1, metrics.getToolErrorCount());
        assertEquals(2, metrics.getTurnsUsed());
    }

    @Test
    void registerHooksConnectsToHookExecutor() {
        HookExecutor hookExecutor = new HookExecutor();
        evolutionEngine.registerHooks(hookExecutor);

        // MetricsCollector 应该被注册到 POST_TOOL_USE
        assertEquals(1, hookExecutor.handlerCount(HookEvent.POST_TOOL_USE));
    }

    @Test
    void metricsCollectorReceivesToolCallsViaHook() {
        HookExecutor hookExecutor = new HookExecutor();
        evolutionEngine.registerHooks(hookExecutor);
        evolutionEngine.onSessionStart("session-1", 12);

        // 通过 Hook 发送工具调用事件
        hookExecutor.fire(HookEvent.POST_TOOL_USE, Map.of(
                "sessionId", "session-1",
                "toolName", "grep",
                "success", true
        ));

        hookExecutor.fire(HookEvent.POST_TOOL_USE, Map.of(
                "sessionId", "session-1",
                "toolName", "bash",
                "success", false
        ));

        // 等待异步 Hook 执行
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        SessionMetrics metrics = evolutionEngine.getMetricsCollector().getMetrics("session-1");
        assertNotNull(metrics);
        assertEquals(2, metrics.getToolSequence().size());
        assertEquals(1, metrics.getToolErrorCount());
    }

    @Test
    void experienceExtractionTriggeredOnSessionEnd() {
        // Mock LLM 返回经验 JSON
        String mockExperienceJson = """
                ```json
                {
                  "taskPattern": "文件搜索与替换",
                  "strategy": "使用 grep 定位然后 sed 替换",
                  "lesson": "替换前先备份",
                  "keywords": ["搜索", "替换", "文件"],
                  "success": true
                }
                ```
                """;
        when(mockLlmClient.chatStream(any(), any(), any()))
                .thenReturn(new LlmResponse(mockExperienceJson, null, 100, 200));

        // 开始会话
        evolutionEngine.onSessionStart("session-1", 12);
        evolutionEngine.recordTurnIncrement("session-1");
        evolutionEngine.recordTurnIncrement("session-1");
        evolutionEngine.recordTurnIncrement("session-1");
        evolutionEngine.recordToolCall("session-1", "grep", true);
        evolutionEngine.recordToolCall("session-1", "bash", true);

        // 模拟会话消息
        List<ConversationMessage> messages = List.of(
                ConversationMessage.system("你是助手"),
                ConversationMessage.user("帮我搜索并替换所有文件中的 foo 为 bar"),
                ConversationMessage.assistant("好的，我来帮你完成这个任务。")
        );

        // 结束会话 → 触发经验提取
        evolutionEngine.onSessionEnd("session-1", "user1", messages, 500, 300);

        // 等待异步提取完成
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        // 验证经验已保存
        assertEquals(1, evolutionEngine.getExperienceStore().count("user1"));
    }

    @Test
    void experiencePromptSectionReturnsEmptyWhenNoExperiences() {
        String section = evolutionEngine.getExperiencePromptSection("user1", "做一个新功能");
        assertEquals("", section);
    }

    @Test
    void experiencePromptSectionReturnsContentAfterExtraction() {
        // 直接向 store 写入经验
        var exp = io.leavesfly.jharness2.engine.ext.evolution.experience.Experience.create(
                "user1", "REST API 开发",
                List.of("file_write", "bash"),
                "先定义接口再实现",
                true,
                "记得写单元测试",
                List.of("api", "rest", "开发"),
                5, 2000L
        );
        evolutionEngine.getExperienceStore().save(exp);

        String section = evolutionEngine.getExperiencePromptSection("user1", "开发一个 REST API");
        assertFalse(section.isEmpty());
        assertTrue(section.contains("REST API 开发"));
        assertTrue(section.contains("记得写单元测试"));
    }

    @Test
    void sessionEndSkipsExtractionWhenTurnsInsufficient() {
        config.setMinTurnsToExtract(5);
        evolutionEngine = new EvolutionEngine(mockLlmClient, tempDir, config);

        evolutionEngine.onSessionStart("session-1", 12);
        evolutionEngine.recordTurnIncrement("session-1"); // 只有 1 轮

        List<ConversationMessage> messages = List.of(
                ConversationMessage.user("hi"),
                ConversationMessage.assistant("hello")
        );

        evolutionEngine.onSessionEnd("session-1", "user1", messages, 50, 30);

        // 等待
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        // 不应该调用 LLM
        verify(mockLlmClient, never()).chatStream(any(), any(), any());
        assertEquals(0, evolutionEngine.getExperienceStore().count("user1"));
    }

    @Test
    void configDisabledPreventsExperienceRetrieval() {
        config.setExperienceEnabled(false);
        evolutionEngine = new EvolutionEngine(mockLlmClient, tempDir, config);

        String section = evolutionEngine.getExperiencePromptSection("user1", "任何任务");
        assertEquals("", section);
    }
}
