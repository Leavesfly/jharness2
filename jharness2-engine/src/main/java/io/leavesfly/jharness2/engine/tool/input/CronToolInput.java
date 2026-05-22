package io.leavesfly.jharness2.engine.tool.input;

/**
 * Cron 工具输入参数。
 * <p>
 * action 取值：register, list, remove, pause, resume, trigger
 */
public class CronToolInput {

    /** 操作类型：register | list | remove | pause | resume | trigger */
    private String action;

    /** 任务名称（register 时必填） */
    private String name;

    // cron 表达式，5 段格式（register 时必填），例如 "0/5 * * * *"
    private String cron_expression;

    /** 触发时要执行的 shell 命令（register 时必填） */
    private String command;

    /** 任务 ID（remove / pause / resume / trigger 时必填） */
    private String job_id;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCron_expression() { return cron_expression; }
    public void setCron_expression(String cron_expression) { this.cron_expression = cron_expression; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getJob_id() { return job_id; }
    public void setJob_id(String job_id) { this.job_id = job_id; }
}
