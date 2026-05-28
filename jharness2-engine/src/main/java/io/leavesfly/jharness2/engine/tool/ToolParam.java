package io.leavesfly.jharness2.engine.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数注解 — 为工具输入字段提供丰富的元信息，用于生成高质量的 JSON Schema。
 * <p>
 * LLM 依赖 JSON Schema 中的 description 来理解参数的含义和用法，
 * 缺少 description 会显著降低工具调用的准确率。
 *
 * <pre>
 * public class ReadFileInput {
 *     {@literal @}ToolParam(description = "文件的相对路径", required = true)
 *     private String filePath;
 *
 *     {@literal @}ToolParam(description = "是否读取整个文件", defaultValue = "false")
 *     private boolean entireFile;
 *
 *     {@literal @}ToolParam(description = "输出格式", enumValues = {"json", "text", "markdown"})
 *     private String format;
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ToolParam {

    /** 参数描述 — 帮助 LLM 理解何时/如何使用此参数 */
    String description() default "";

    /** 是否必填（优先级高于 @NotNull/@NotBlank 检测） */
    boolean required() default false;

    /** 枚举可选值 — 限制参数取值范围 */
    String[] enumValues() default {};

    /** 默认值（字符串形式，仅作文档用途） */
    String defaultValue() default "";

    /** 示例值（帮助 LLM 理解期望格式） */
    String example() default "";
}
