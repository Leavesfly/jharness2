package io.leavesfly.jharness2.core.engine.stream;

public class AssistantTurnComplete extends StreamEvent {
    private final int turnsUsed;

    public AssistantTurnComplete(int turnsUsed) {
        this.turnsUsed = turnsUsed;
    }

    @Override
    public String getEventType() {
        return "assistant_turn_complete";
    }

    public int getTurnsUsed() { return turnsUsed; }
}
