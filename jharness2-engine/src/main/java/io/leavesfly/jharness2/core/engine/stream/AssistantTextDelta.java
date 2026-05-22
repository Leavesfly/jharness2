package io.leavesfly.jharness2.core.engine.stream;

public class AssistantTextDelta extends StreamEvent {
    private final String text;

    public AssistantTextDelta(String text) {
        this.text = text;
    }

    @Override
    public String getEventType() {
        return "assistant_text_delta";
    }

    public String getText() {
        return text;
    }
}
