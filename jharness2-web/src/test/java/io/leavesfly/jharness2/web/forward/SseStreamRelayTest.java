package io.leavesfly.jharness2.web.forward;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SSE 流中继解析测试 —— 节点间转发时对上游 text/event-stream 的解析正确性。
 */
class SseStreamRelayTest {

    private record Event(String name, String data) {}

    private static List<Event> relay(String stream) throws IOException {
        List<Event> events = new ArrayList<>();
        SseStreamRelay.relay(new BufferedReader(new StringReader(stream)),
                (name, data) -> events.add(new Event(name, data)));
        return events;
    }

    @Test
    void shouldParseNamedEvents() throws IOException {
        String stream = """
                event: text
                data: {"type":"text","content":"hello"}

                event: done
                data: {"type":"done"}

                """;
        List<Event> events = relay(stream);

        assertEquals(2, events.size());
        assertEquals("text", events.get(0).name());
        assertEquals("{\"type\":\"text\",\"content\":\"hello\"}", events.get(0).data());
        assertEquals("done", events.get(1).name());
    }

    @Test
    void shouldDefaultToMessageWhenEventNameAbsent() throws IOException {
        List<Event> events = relay("data: {\"a\":1}\n\n");

        assertEquals(1, events.size());
        assertEquals("message", events.get(0).name());
        assertEquals("{\"a\":1}", events.get(0).data());
    }

    @Test
    void shouldIgnoreCommentLines() throws IOException {
        String stream = """
                : keep-alive
                event: text
                data: {"x":1}

                """;
        List<Event> events = relay(stream);

        assertEquals(1, events.size());
        assertEquals("text", events.get(0).name());
    }

    @Test
    void shouldJoinMultiLineData() throws IOException {
        String stream = """
                event: text
                data: line1
                data: line2

                """;
        List<Event> events = relay(stream);

        assertEquals(1, events.size());
        assertEquals("line1\nline2", events.get(0).data());
    }

    @Test
    void shouldFlushTrailingEventWithoutClosingBlankLine() throws IOException {
        // 上游异常中断：最后一个事件没有以空行收尾，也应转发
        List<Event> events = relay("event: error\ndata: {\"error\":\"boom\"}");

        assertEquals(1, events.size());
        assertEquals("error", events.get(0).name());
        assertEquals("{\"error\":\"boom\"}", events.get(0).data());
    }

    @Test
    void shouldStopWhenDownstreamDisconnects() {
        String stream = """
                event: text
                data: {"x":1}

                event: text
                data: {"x":2}

                """;
        assertThrows(IOException.class, () ->
                SseStreamRelay.relay(new BufferedReader(new StringReader(stream)),
                        (name, data) -> { throw new IOException("client gone"); }));
    }
}
