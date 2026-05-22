package io.leavesfly.jharness2.core.engine.model;

/**
 * 消息内容块抽象基类。
 * 支持多种内容类型：文本、工具调用请求、工具调用结果。
 */
public abstract class ContentBlock {
    public abstract String getType();
}
