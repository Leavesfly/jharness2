package io.leavesfly.jharness2.core.engine.tool.input;

/**
 * SkillTool 的输入参数。
 * - action: "list" 列出所有技能，"get" 获取指定技能的完整内容
 * - name: 当 action 为 "get" 时，指定要获取的技能名称
 */
public class SkillInput {
    private String action;
    private String name;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
