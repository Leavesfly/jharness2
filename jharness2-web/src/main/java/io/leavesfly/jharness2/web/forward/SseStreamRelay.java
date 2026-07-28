package io.leavesfly.jharness2.web.forward;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * SSE 流中继解析器 —— 逐行解析上游 text/event-stream，按事件回调下游。
 * <p>
 * 仅处理转发场景需要的 event/data 字段（data 为单行 JSON，即本服务自身的输出格式），
 * 注释行（: 开头）与其他字段忽略。
 */
public final class SseStreamRelay {

    private SseStreamRelay() {}

    /** 事件回调：抛出 IOException 表示下游连接已断开，中继应停止 */
    @FunctionalInterface
    public interface EventSink {
        void onEvent(String eventName, String data) throws IOException;
    }

    /**
     * 从上游 reader 读取 SSE 流并转发给 sink，直到流结束或下游断开。
     *
     * @return 成功转发的事件数
     */
    public static int relay(BufferedReader reader, EventSink sink) throws IOException {
        int relayed = 0;
        String eventName = null;
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                // 空行 = 事件边界
                if (data.length() > 0 || eventName != null) {
                    sink.onEvent(eventName != null ? eventName : "message", data.toString());
                    relayed++;
                }
                eventName = null;
                data.setLength(0);
                continue;
            }
            if (line.startsWith(":")) {
                continue; // 注释/心跳行
            }
            if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).stripLeading());
            }
            // 其他字段（id/retry）转发场景不需要，忽略
        }
        // 流结束时冲刷未闭合的最后一个事件（上游异常中断的兜底）
        if (data.length() > 0 || eventName != null) {
            sink.onEvent(eventName != null ? eventName : "message", data.toString());
            relayed++;
        }
        return relayed;
    }
}
