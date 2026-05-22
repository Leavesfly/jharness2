package io.leavesfly.jharness2.engine.tool.builtin.cron;

import io.leavesfly.jharness2.engine.cron.CronScheduler;
import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.engine.tool.ToolResult;
import io.leavesfly.jharness2.engine.tool.input.CronToolInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CronToolTest {

    private CronScheduler scheduler;
    private CronTool cronTool;
    private ToolExecutionContext context;

    @BeforeEach
    void setUp() {
        scheduler = new CronScheduler();
        cronTool = new CronTool(scheduler);
        context = new ToolExecutionContext(Path.of("."), null);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @Test
    void shouldReturnErrorForBlankAction() {
        CronToolInput input = new CronToolInput();
        ToolResult result = cronTool.execute(input, context).join();
        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("action"));
    }

    @Test
    void shouldReturnErrorForUnknownAction() {
        CronToolInput input = new CronToolInput();
        input.setAction("unknown");
        ToolResult result = cronTool.execute(input, context).join();
        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("未知的 action"));
    }

    @Test
    void shouldRegisterCronJob() {
        CronToolInput input = new CronToolInput();
        input.setAction("register");
        input.setName("test-job");
        input.setCron_expression("*/5 * * * *");
        input.setCommand("echo hello");

        ToolResult result = cronTool.execute(input, context).join();
        assertFalse(result.isError());
        assertTrue(result.getOutput().contains("注册成功"));
        assertTrue(result.getOutput().contains("test-job"));
        assertTrue(result.getOutput().contains("job_id"));

        assertEquals(1, scheduler.listJobs().size());
        assertTrue(scheduler.isRunning(), "Scheduler should auto-start on register");
    }

    @Test
    void shouldRejectRegisterWithMissingFields() {
        // missing name
        CronToolInput input1 = new CronToolInput();
        input1.setAction("register");
        input1.setCron_expression("* * * * *");
        input1.setCommand("echo hi");
        assertTrue(cronTool.execute(input1, context).join().isError());

        // missing cron_expression
        CronToolInput input2 = new CronToolInput();
        input2.setAction("register");
        input2.setName("job");
        input2.setCommand("echo hi");
        assertTrue(cronTool.execute(input2, context).join().isError());

        // missing command
        CronToolInput input3 = new CronToolInput();
        input3.setAction("register");
        input3.setName("job");
        input3.setCron_expression("* * * * *");
        assertTrue(cronTool.execute(input3, context).join().isError());
    }

    @Test
    void shouldRejectInvalidCronExpression() {
        CronToolInput input = new CronToolInput();
        input.setAction("register");
        input.setName("bad-cron");
        input.setCron_expression("invalid");
        input.setCommand("echo hi");

        ToolResult result = cronTool.execute(input, context).join();
        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("cron 表达式格式错误"));
    }

    @Test
    void shouldListJobs() {
        // empty list
        CronToolInput listInput = new CronToolInput();
        listInput.setAction("list");
        ToolResult emptyResult = cronTool.execute(listInput, context).join();
        assertFalse(emptyResult.isError());
        assertTrue(emptyResult.getOutput().contains("没有注册"));

        // register then list
        CronToolInput regInput = new CronToolInput();
        regInput.setAction("register");
        regInput.setName("listed-job");
        regInput.setCron_expression("0 9 * * *");
        regInput.setCommand("echo listed");
        cronTool.execute(regInput, context).join();

        ToolResult listResult = cronTool.execute(listInput, context).join();
        assertFalse(listResult.isError());
        assertTrue(listResult.getOutput().contains("listed-job"));
        assertTrue(listResult.getOutput().contains("共 1 个"));
    }

    @Test
    void shouldRemoveJob() {
        String jobId = registerTestJob();

        CronToolInput input = new CronToolInput();
        input.setAction("remove");
        input.setJob_id(jobId);

        ToolResult result = cronTool.execute(input, context).join();
        assertFalse(result.isError());
        assertTrue(result.getOutput().contains("已移除"));
        assertTrue(scheduler.listJobs().isEmpty());
    }

    @Test
    void shouldReturnErrorForRemoveNonexistent() {
        CronToolInput input = new CronToolInput();
        input.setAction("remove");
        input.setJob_id("nonexistent");

        ToolResult result = cronTool.execute(input, context).join();
        assertTrue(result.isError());
    }

    @Test
    void shouldPauseAndResumeJob() {
        String jobId = registerTestJob();

        // pause
        CronToolInput pauseInput = new CronToolInput();
        pauseInput.setAction("pause");
        pauseInput.setJob_id(jobId);
        ToolResult pauseResult = cronTool.execute(pauseInput, context).join();
        assertFalse(pauseResult.isError());
        assertTrue(pauseResult.getOutput().contains("已暂停"));

        // resume
        CronToolInput resumeInput = new CronToolInput();
        resumeInput.setAction("resume");
        resumeInput.setJob_id(jobId);
        ToolResult resumeResult = cronTool.execute(resumeInput, context).join();
        assertFalse(resumeResult.isError());
        assertTrue(resumeResult.getOutput().contains("已恢复"));
    }

    @Test
    void shouldTriggerJob() {
        String jobId = registerTestJob();

        CronToolInput input = new CronToolInput();
        input.setAction("trigger");
        input.setJob_id(jobId);

        ToolResult result = cronTool.execute(input, context).join();
        assertFalse(result.isError());
        assertTrue(result.getOutput().contains("已手动触发"));
    }

    @Test
    void shouldReturnErrorForTriggerWithMissingJobId() {
        CronToolInput input = new CronToolInput();
        input.setAction("trigger");

        ToolResult result = cronTool.execute(input, context).join();
        assertTrue(result.isError());
        assertTrue(result.getOutput().contains("job_id"));
    }

    @Test
    void shouldBeReadOnlyForListAction() {
        CronToolInput listInput = new CronToolInput();
        listInput.setAction("list");
        assertTrue(cronTool.isReadOnly(listInput));

        CronToolInput regInput = new CronToolInput();
        regInput.setAction("register");
        assertFalse(cronTool.isReadOnly(regInput));
    }

    @Test
    void shouldGenerateFunctionSchema() {
        var schema = cronTool.toFunctionSchema();
        assertNotNull(schema);
        assertEquals("function", schema.get("type"));
        @SuppressWarnings("unchecked")
        var function = (java.util.Map<String, Object>) schema.get("function");
        assertEquals("cron", function.get("name"));
        assertNotNull(function.get("description"));
        assertNotNull(function.get("parameters"));
    }

    private String registerTestJob() {
        CronToolInput input = new CronToolInput();
        input.setAction("register");
        input.setName("test-job");
        input.setCron_expression("0 0 1 1 *");
        input.setCommand("echo test");
        ToolResult result = cronTool.execute(input, context).join();
        assertFalse(result.isError());

        return scheduler.listJobs().get(0).getJobId();
    }
}
