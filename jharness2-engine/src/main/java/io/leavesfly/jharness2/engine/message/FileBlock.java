package io.leavesfly.jharness2.engine.message;

/**
 * 文件内容块 — 表示附加的文件（代码、文档、数据等）。
 *
 * @param fileName  文件名
 * @param mediaType MIME 类型（如 "text/plain"、"application/json"）
 * @param content   文件内容（文本形式）
 */
public record FileBlock(String fileName, String mediaType, String content) implements ContentBlock {

    @Override
    public String getType() {
        return "file";
    }

    /** 文件大小（字符数） */
    public int size() {
        return content != null ? content.length() : 0;
    }
}
