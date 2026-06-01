package io.leavesfly.jharness2.engine.ext.evolution.toolmaker;

import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
import io.leavesfly.jharness2.engine.tool.ToolParam;
import io.leavesfly.jharness2.engine.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 「造工具」内置工具 —— 暴露给 LLM 的入口，让 Agent 可以自主创建新工具。
 * <p>
 * 当 Agent 发现现有工具无法满足当前任务需求时，可调用此工具描述需要的能力，
 * 系统将自动生成、验证并注册新工具。
 */
public class CreateToolTool extends BaseTool<CreateToolTool.Input> {

    private static final Logger logger = LoggerFactory.getLogger(CreateToolTool.class);

    private final ToolMaker toolMaker;

    public CreateToolTool(ToolMaker toolMaker) {
        this.toolMaker = toolMaker;
    }

    @Override
    public String getName() {
        return "create_tool";
    }

    @Override
    public String getDescription() {
        return "当你发现现有工具无法满足当前任务需求时，使用此工具创建一个新的自定义工具。" +
               "你需要描述工具的功能、输入参数和核心逻辑，系统会自动生成、验证并注册该工具供后续使用。" +
               "注意：创建的工具不能使用 Runtime、ProcessBuilder 等危险 API。";
    }

    @Override
    public Class<Input> getInputClass() {
        return Input.class;
    }

    @Override
    public CompletableFuture<ToolResult> execute(Input input, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ToolSpec spec = buildSpec(input);
                ToolCreationResult result = toolMaker.create(spec);

                if (result.isSuccess()) {
                    logger.info("CreateToolTool: successfully created tool '{}'", input.toolName);
                    return ToolResult.success(
                            "✅ 工具 '" + input.toolName + "' 创建成功！\n" +
                            "已注册到工具列表，你可以立即使用它。\n" +
                            "工具描述: " + input.description);
                } else {
                    logger.warn("CreateToolTool: failed to create tool '{}': {}",
                            input.toolName, result.getMessage());
                    return ToolResult.error("❌ 工具创建失败: " + result.getMessage());
                }
            } catch (Exception e) {
                logger.error("CreateToolTool: unexpected error creating tool '{}': {}",
                        input.toolName, e.getMessage());
                return ToolResult.error("工具创建异常: " + e.getMessage());
            }
        });
    }

    @Override
    public boolean isReadOnly(Input input) {
        return false;
    }

    private ToolSpec buildSpec(Input input) {
        ToolSpec spec = new ToolSpec();
        spec.setToolName(input.toolName);
        spec.setDescription(input.description);
        spec.setLogic(input.logic);
        spec.setTestInput(input.testInput);
        spec.setExpectedOutput(input.expectedOutput);

        if (input.parameters != null && !input.parameters.isBlank()) {
            spec.setParameters(parseParameters(input.parameters));
        }

        return spec;
    }

    private List<ToolSpec.ParamSpec> parseParameters(String paramDescription) {
        List<ToolSpec.ParamSpec> params = new ArrayList<>();
        String[] lines = paramDescription.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            // 解析格式: "name (type): description" 或 "name: description"
            String name = line;
            String type = "String";
            String desc = "";
            boolean required = false;

            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                name = line.substring(0, colonIdx).trim();
                desc = line.substring(colonIdx + 1).trim();
            }

            int parenStart = name.indexOf('(');
            if (parenStart > 0) {
                int parenEnd = name.indexOf(')');
                if (parenEnd > parenStart) {
                    type = name.substring(parenStart + 1, parenEnd).trim();
                    name = name.substring(0, parenStart).trim();
                }
            }

            if (name.endsWith("*")) {
                required = true;
                name = name.substring(0, name.length() - 1).trim();
            }

            params.add(new ToolSpec.ParamSpec(name, type, desc, required));
        }
        return params;
    }

    // --- Input 定义 ---

    public static class Input {
        @ToolParam(description = "工具名称，使用 snake_case 格式（例如：csv_parser、url_shortener）", required = true)
        public String toolName;

        @ToolParam(description = "工具的功能描述，帮助你（和其他人）理解何时使用此工具", required = true)
        public String description;

        @ToolParam(description = "输入参数定义，每行一个，格式：参数名 (类型): 描述。例如：\nfilePath (String): 文件路径\nformat (String): 输出格式")
        public String parameters;

        @ToolParam(description = "工具的核心逻辑描述（自然语言），描述这个工具应该做什么", required = true)
        public String logic;

        @ToolParam(description = "测试输入示例（JSON 格式），用于验证工具是否正常工作")
        public String testInput;

        @ToolParam(description = "预期输出示例，用于验证工具行为是否正确")
        public String expectedOutput;
    }
}
