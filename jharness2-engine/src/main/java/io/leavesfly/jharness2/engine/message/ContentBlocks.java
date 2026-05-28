package io.leavesfly.jharness2.engine.message;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ContentBlock 工具类 — 提供便捷的构造和转换方法。
 */
public final class ContentBlocks {

    private ContentBlocks() {}

    /** 从纯文本创建单元素列表 */
    public static List<ContentBlock> ofText(String text) {
        return List.of(new TextBlock(text));
    }

    /** 构建混合内容列表 */
    public static Builder builder() {
        return new Builder();
    }

    /** 将 ContentBlock 列表转为纯文本（丢弃非文本内容） */
    public static String toPlainText(List<ContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return "";
        return blocks.stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).text())
                .collect(Collectors.joining("\n"));
    }

    /** 判断列表是否包含非文本内容 */
    public static boolean hasMultimodal(List<ContentBlock> blocks) {
        if (blocks == null) return false;
        return blocks.stream().anyMatch(b -> !(b instanceof TextBlock));
    }

    public static class Builder {
        private final List<ContentBlock> blocks = new ArrayList<>();

        public Builder text(String text) {
            blocks.add(new TextBlock(text));
            return this;
        }

        public Builder image(String url, String mediaType) {
            blocks.add(ImageBlock.fromUrl(url, mediaType));
            return this;
        }

        public Builder imageBase64(String data, String mediaType) {
            blocks.add(ImageBlock.fromBase64(data, mediaType));
            return this;
        }

        public Builder file(String fileName, String mediaType, String content) {
            blocks.add(new FileBlock(fileName, mediaType, content));
            return this;
        }

        public List<ContentBlock> build() {
            return List.copyOf(blocks);
        }
    }
}
