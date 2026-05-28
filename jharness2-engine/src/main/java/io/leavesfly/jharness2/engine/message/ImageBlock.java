package io.leavesfly.jharness2.engine.message;

/**
 * 图片内容块 — 支持 URL 引用和 Base64 内联两种方式。
 *
 * @param mediaType  MIME 类型（如 "image/png"、"image/jpeg"）
 * @param source     图片来源（URL 或 Base64 编码数据）
 * @param sourceType 来源类型
 */
public record ImageBlock(String mediaType, String source, SourceType sourceType) implements ContentBlock {

    public enum SourceType {
        /** URL 引用 */
        URL,
        /** Base64 内联数据 */
        BASE64
    }

    /** 从 URL 创建 */
    public static ImageBlock fromUrl(String url, String mediaType) {
        return new ImageBlock(mediaType, url, SourceType.URL);
    }

    /** 从 Base64 数据创建 */
    public static ImageBlock fromBase64(String base64Data, String mediaType) {
        return new ImageBlock(mediaType, base64Data, SourceType.BASE64);
    }

    @Override
    public String getType() {
        return "image";
    }
}
