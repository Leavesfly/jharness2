package io.leavesfly.jharness2.engine.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表 —— 管理所有已注册的 {@link BaseTool}，提供查询和 API Schema 生成。
 */
public class ToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ToolRegistry.class);
    private final Map<String, BaseTool<?>> tools = new ConcurrentHashMap<>();

    /**
     * 注册工具（重复注册时跳过并警告）。
     */
    public void register(BaseTool<?> tool) {
        if (tool == null) throw new IllegalArgumentException("tool must not be null");
        String name = tool.getName();
        BaseTool<?> existing = tools.putIfAbsent(name, tool);
        if (existing != null) {
            logger.warn("Tool name conflict, skipping: name={}, existing={}, skipped={}",
                    name, existing.getClass().getSimpleName(), tool.getClass().getSimpleName());
            return;
        }
        logger.debug("Registered tool: {}", name);
    }

    /** 获取工具实例 */
    public BaseTool<?> get(String name) {
        return tools.get(name);
    }

    /** 检查是否存在 */
    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /** 取消注册 */
    public boolean unregister(String name) {
        return tools.remove(name) != null;
    }

    /** 获取所有工具名 */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /** 获取所有工具 */
    public Collection<BaseTool<?>> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * 生成 OpenAI function calling 格式的 tools 列表。
     */
    public List<Map<String, Object>> toApiSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (BaseTool<?> tool : new ArrayList<>(tools.values())) {
            schemas.add(tool.toFunctionSchema());
        }
        return schemas;
    }

    public int size() {
        return tools.size();
    }
}
