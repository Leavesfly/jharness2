package io.leavesfly.jharness2.engine.message;

/**
 * 文本内容块。
 */
public record TextBlock(String text) implements ContentBlock {

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public String toString() {
        return text;
    }
}
