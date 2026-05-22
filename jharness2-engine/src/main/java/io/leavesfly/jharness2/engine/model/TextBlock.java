package io.leavesfly.jharness2.engine.model;

/**
 * 文本内容块。
 */
public class TextBlock extends ContentBlock {
    private final String text;

    public TextBlock(String text) {
        this.text = text;
    }

    @Override
    public String getType() { return "text"; }

    public String getText() { return text; }

    @Override
    public String toString() {
        return "TextBlock{text='" + text + "'}";
    }
}
