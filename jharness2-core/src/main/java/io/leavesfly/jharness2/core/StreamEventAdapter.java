package io.leavesfly.jharness2.core;

import io.leavesfly.jharness2.engine.stream.AssistantTextDelta;
import io.leavesfly.jharness2.engine.stream.AssistantTurnComplete;
import io.leavesfly.jharness2.engine.stream.StreamEvent;
import io.leavesfly.jharness2.engine.stream.ToolExecutionCompleted;
import io.leavesfly.jharness2.engine.stream.ToolExecutionStarted;
import io.leavesfly.jharness2.engine.stream.UsageReport;

/**
 * 将内核 {@link StreamEvent} 转换为 Web 层可序列化的 DTO。
 * 类型安全，避免反射。
 */
public final class StreamEventAdapter {

    private StreamEventAdapter() {}

    public static ChatEventDto adapt(StreamEvent event) {
        if (event instanceof AssistantTextDelta delta) {
            return ChatEventDto.text(delta.getText());
        }
        if (event instanceof ToolExecutionStarted started) {
            return ChatEventDto.toolStart(started.getToolName(), started.getToolId());
        }
        if (event instanceof ToolExecutionCompleted completed) {
            return ChatEventDto.toolEnd(completed.getToolName(), completed.getResult(), completed.isError());
        }
        if (event instanceof AssistantTurnComplete) {
            return ChatEventDto.done();
        }
        if (event instanceof UsageReport report) {
            return ChatEventDto.usage(report.getInputTokens(), report.getOutputTokens(),
                    report.getSessionCostUsd());
        }
        return null;
    }
}
