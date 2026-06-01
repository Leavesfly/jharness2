package io.leavesfly.jharness2.engine.ext.evolution.toolmaker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * 工具规格描述 —— 描述一个待生成工具的需求。
 * <p>
 * 由 LLM 通过 CreateToolTool 提交，ToolMaker 根据此规格生成工具代码。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolSpec {

    private String toolName;
    private String description;
    private List<ParamSpec> parameters;
    private String logic;
    private String testInput;
    private String expectedOutput;
    private Instant createdAt;

    public ToolSpec() {
        this.createdAt = Instant.now();
    }

    /**
     * 参数规格。
     */
    public static class ParamSpec {
        private String name;
        private String type;
        private String description;
        private boolean required;

        public ParamSpec() {}

        public ParamSpec(String name, String type, String description, boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }

    // --- Getters and Setters ---

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<ParamSpec> getParameters() { return parameters; }
    public void setParameters(List<ParamSpec> parameters) { this.parameters = parameters; }

    public String getLogic() { return logic; }
    public void setLogic(String logic) { this.logic = logic; }

    public String getTestInput() { return testInput; }
    public void setTestInput(String testInput) { this.testInput = testInput; }

    public String getExpectedOutput() { return expectedOutput; }
    public void setExpectedOutput(String expectedOutput) { this.expectedOutput = expectedOutput; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
