package io.leavesfly.jharness2.engine;

import io.leavesfly.jharness2.engine.message.ConversationMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 悬空 tool_calls 修复测试 —— 恢复持久化历史时，为缺失 tool_result 的
 * tool_call 补合成结果，防止 LLM API 因消息不成对而报错。
 */
class ConversationHistoryRepairTest {

    private static ConversationMessage assistantWithCalls(String... callIds) {
        List<ConversationMessage.ToolCall> calls = java.util.Arrays.stream(callIds)
                .map(id -> new ConversationMessage.ToolCall(id, "function",
                        new ConversationMessage.FunctionCall("some_tool", "{}")))
                .toList();
        return ConversationMessage.assistantWithToolCalls("thinking...", calls);
    }

    @Test
    void shouldKeepIntactHistoryUnchanged() {
        List<ConversationMessage> history = List.of(
                ConversationMessage.system("sys"),
                ConversationMessage.user("hi"),
                assistantWithCalls("call-1"),
                ConversationMessage.toolResult("call-1", "some_tool", "ok"),
                ConversationMessage.assistant("done"));

        List<ConversationMessage> repaired = QueryEngine.repairDanglingToolCalls(history);

        assertEquals(history.size(), repaired.size());
        for (int i = 0; i < history.size(); i++) {
            assertSame(history.get(i), repaired.get(i));
        }
    }

    @Test
    void shouldSynthesizeResultsForFullyDanglingToolCalls() {
        // 中断场景：assistant 发出两个 tool_call 后引擎被强杀，没有任何 tool_result
        List<ConversationMessage> history = List.of(
                ConversationMessage.system("sys"),
                ConversationMessage.user("hi"),
                assistantWithCalls("call-1", "call-2"));

        List<ConversationMessage> repaired = QueryEngine.repairDanglingToolCalls(history);

        assertEquals(5, repaired.size());
        ConversationMessage synth1 = repaired.get(3);
        ConversationMessage synth2 = repaired.get(4);
        assertEquals(ConversationMessage.Role.TOOL, synth1.getRole());
        assertEquals("call-1", synth1.getToolCallId());
        assertEquals("call-2", synth2.getToolCallId());
        assertTrue(synth1.getContent().contains("interrupted"));
    }

    @Test
    void shouldSynthesizeOnlyMissingResultsForPartiallyDanglingToolCalls() {
        // 部分完成场景：两个 tool_call 只落库了一个结果
        List<ConversationMessage> history = List.of(
                ConversationMessage.user("hi"),
                assistantWithCalls("call-1", "call-2"),
                ConversationMessage.toolResult("call-1", "some_tool", "ok"));

        List<ConversationMessage> repaired = QueryEngine.repairDanglingToolCalls(history);

        assertEquals(4, repaired.size());
        assertEquals("call-1", repaired.get(2).getToolCallId());
        ConversationMessage synth = repaired.get(3);
        assertEquals(ConversationMessage.Role.TOOL, synth.getRole());
        assertEquals("call-2", synth.getToolCallId());
        assertTrue(synth.getContent().contains("interrupted"));
    }

    @Test
    void shouldRepairMiddleOfHistoryAndPreserveOrder() {
        // 悬空点在历史中间（后面还有新一轮对话）
        List<ConversationMessage> history = List.of(
                ConversationMessage.user("q1"),
                assistantWithCalls("call-1"),
                ConversationMessage.user("q2"),
                ConversationMessage.assistant("a2"));

        List<ConversationMessage> repaired = QueryEngine.repairDanglingToolCalls(history);

        assertEquals(5, repaired.size());
        assertEquals(ConversationMessage.Role.TOOL, repaired.get(2).getRole());
        assertEquals("call-1", repaired.get(2).getToolCallId());
        assertEquals("q2", repaired.get(3).getContent());
        assertEquals("a2", repaired.get(4).getContent());
    }

    @Test
    void loadMessagesShouldApplyRepair() {
        QueryEngine engine = new QueryEngine(null, new io.leavesfly.jharness2.engine.tool.ToolRegistry(),
                "sys", 5);
        engine.loadMessages(List.of(
                ConversationMessage.user("hi"),
                assistantWithCalls("call-1")));

        List<ConversationMessage> messages = engine.getMessages();
        assertEquals(3, messages.size());
        assertEquals(ConversationMessage.Role.TOOL, messages.get(2).getRole());
        assertEquals("call-1", messages.get(2).getToolCallId());
    }
}
