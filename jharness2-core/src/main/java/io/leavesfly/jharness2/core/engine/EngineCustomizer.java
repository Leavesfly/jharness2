package io.leavesfly.jharness2.core.engine;

import io.leavesfly.jharness2.core.UserContext;
import io.leavesfly.jharness2.engine.QueryEngine;

import java.nio.file.Path;

/**
 * 引擎定制器 SPI —— 各子系统实现此接口完成对 QueryEngine 的定制初始化。
 * <p>
 * 通过此 SPI，DefaultEngineFactory 不再需要硬编码所有子系统的初始化逻辑，
 * 每个 Customizer 高内聚于自己的子系统，遵循单一职责和开闭原则。
 * <p>
 * 执行顺序由 {@link #getOrder()} 决定，数值越小越先执行。
 */
public interface EngineCustomizer {

    /**
     * 定制引擎实例。
     *
     * @param engine    已构建的基础 QueryEngine（含 LLM 客户端和工具注册表）
     * @param context   用户上下文
     * @param workspace 用户工作空间路径
     */
    void customize(QueryEngine engine, UserContext context, Path workspace);

    /**
     * 执行顺序，越小越先执行。
     */
    default int getOrder() { return 0; }
}
