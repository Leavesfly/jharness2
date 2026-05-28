package io.leavesfly.jharness2.engine.tool.builtin.meta;

import io.leavesfly.jharness2.engine.ext.skill.SkillRegistry;
import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.engine.tool.ToolResult;
import io.leavesfly.jharness2.engine.tool.input.SkillInput;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 技能工具 - LLM 通过此工具查询和加载已注册的 Skill。
 * <p>
 * 支持两种操作：
 * - action="list": 列出所有可用技能及描述
 * - action="get":  获取指定技能的完整内容（注入到对话上下文）
 */
public class SkillTool extends BaseTool<SkillInput> {

    private final SkillRegistry skillRegistry;

    public SkillTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String getName() { return "skill"; }

    @Override
    public String getDescription() {
        return "查询和加载可用技能。action=\"list\" 列出所有技能，action=\"get\" + name 获取指定技能的完整内容。";
    }

    @Override
    public Class<SkillInput> getInputClass() { return SkillInput.class; }

    @Override
    public boolean isReadOnly(SkillInput input) { return true; }

    @Override
    public CompletableFuture<ToolResult> execute(SkillInput input, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            String action = input.getAction() != null ? input.getAction() : "list";

            return switch (action) {
                case "list" -> listSkills();
                case "get" -> getSkill(input.getName());
                default -> ToolResult.error("未知 action: " + action + "，支持 list / get");
            };
        });
    }

    private ToolResult listSkills() {
        if (skillRegistry.size() == 0) {
            return ToolResult.success("当前没有可用的技能。");
        }
        String listing = skillRegistry.getAll().stream()
                .map(s -> "- " + s.getName() + ": " + s.getDescription())
                .collect(Collectors.joining("\n"));
        return ToolResult.success("可用技能 (" + skillRegistry.size() + " 个):\n" + listing);
    }

    private ToolResult getSkill(String name) {
        if (name == null || name.isBlank()) {
            return ToolResult.error("请指定要获取的技能名称 (name 参数)");
        }
        return skillRegistry.get(name)
                .map(skill -> ToolResult.success(skill.getContent()))
                .orElse(ToolResult.error("未找到技能: " + name));
    }
}
