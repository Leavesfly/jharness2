package io.leavesfly.jharness2.engine.message;

/**
 * 内容块基类 — 消息的最小内容单元。
 * <p>
 * 支持多模态内容组合，一条消息可包含多个 ContentBlock（文本 + 图片 + 文件等）。
 * 设计参考 Anthropic Messages API 和 OpenAI 多模态消息模型。
 *
 * <pre>
 * ConversationMessage {
 *   role: USER,
 *   content: [
 *     TextBlock("请分析这张图片"),
 *     ImageBlock("image/png", "https://..."),
 *     FileBlock("data.csv", "text/csv", "...")
 *   ]
 * }
 * </pre>
 */
public sealed interface ContentBlock permits TextBlock, ImageBlock, FileBlock {

    /** 内容块类型标识 */
    String getType();
}
