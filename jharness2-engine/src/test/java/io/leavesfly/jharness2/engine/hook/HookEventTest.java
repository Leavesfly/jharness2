package io.leavesfly.jharness2.engine.hook;

import io.leavesfly.jharness2.engine.ext.hook.HookEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HookEventTest {

    @Test
    void shouldCreateCustomEvent() {
        HookEvent customEvent = HookEvent.of("custom_event");
        assertEquals("custom_event", customEvent.getValue());
    }

    @Test
    void shouldHaveBuiltInEvents() {
        assertNotNull(HookEvent.SESSION_START);
        assertNotNull(HookEvent.SESSION_END);
        assertNotNull(HookEvent.USER_PROMPT_SUBMIT);
        assertNotNull(HookEvent.STOP);
        assertNotNull(HookEvent.PRE_TOOL_USE);
        assertNotNull(HookEvent.POST_TOOL_USE);
        assertNotNull(HookEvent.SUBAGENT_STOP);
        assertNotNull(HookEvent.NOTIFICATION);

        assertEquals("session_start", HookEvent.SESSION_START.getValue());
        assertEquals("session_end", HookEvent.SESSION_END.getValue());
        assertEquals("user_prompt_submit", HookEvent.USER_PROMPT_SUBMIT.getValue());
        assertEquals("stop", HookEvent.STOP.getValue());
        assertEquals("pre_tool_use", HookEvent.PRE_TOOL_USE.getValue());
        assertEquals("post_tool_use", HookEvent.POST_TOOL_USE.getValue());
        assertEquals("subagent_stop", HookEvent.SUBAGENT_STOP.getValue());
        assertEquals("notification", HookEvent.NOTIFICATION.getValue());
    }

    @Test
    void shouldBeEqualWhenSameValue() {
        HookEvent event1 = HookEvent.of("test_event");
        HookEvent event2 = HookEvent.of("test_event");

        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenDifferentValues() {
        HookEvent event1 = HookEvent.of("event1");
        HookEvent event2 = HookEvent.of("event2");

        assertNotEquals(event1, event2);
    }

    @Test
    void shouldBeEqualForBuiltInEventsWithSameInstance() {
        assertEquals(HookEvent.SESSION_START, HookEvent.SESSION_START);
    }

    @Test
    void shouldBeEqualForBuiltInEventsWithSameValue() {
        HookEvent sessionStartCopy = HookEvent.of("session_start");
        assertEquals(HookEvent.SESSION_START, sessionStartCopy);
    }

    @Test
    void shouldHaveConsistentHashCode() {
        HookEvent event = HookEvent.of("test");
        int hash1 = event.hashCode();
        int hash2 = event.hashCode();

        assertEquals(hash1, hash2);
    }

    @Test
    void shouldHaveReadableToString() {
        HookEvent event = HookEvent.of("my_event");
        String str = event.toString();

        assertTrue(str.contains("my_event"));
    }

    @Test
    void shouldNotBeEqualToNull() {
        HookEvent event = HookEvent.of("test");
        assertNotEquals(null, event);
    }

    @Test
    void shouldNotBeEqualToDifferentType() {
        HookEvent event = HookEvent.of("test");
        assertNotEquals(event, "test");
    }

    @Test
    void shouldBeReflexive() {
        HookEvent event = HookEvent.of("reflexive");
        assertEquals(event, event);
    }

    @Test
    void shouldDistinguishCaseSensitiveValues() {
        HookEvent event1 = HookEvent.of("TestEvent");
        HookEvent event2 = HookEvent.of("testevent");

        assertNotEquals(event1, event2);
    }
}
