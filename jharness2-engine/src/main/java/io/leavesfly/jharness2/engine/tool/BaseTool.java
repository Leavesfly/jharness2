package io.leavesfly.jharness2.engine.tool;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 工具抽象基类。
 * <p>
 * 所有工具必须继承此类并实现 execute 方法。
 * 通过反射 InputClass 的字段自动生成 OpenAI function calling 所需的 JSON Schema。
 *
 * @param <T> 工具输入类型
 */
public abstract class BaseTool<T> {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 工具名称（对应 function calling 的 name） */
    public abstract String getName();

    /** 工具描述（帮助 LLM 理解何时使用此工具） */
    public abstract String getDescription();

    /** 输入参数的 Java 类型 */
    public abstract Class<T> getInputClass();

    /** 执行工具 */
    public abstract CompletableFuture<ToolResult> execute(T input, ToolExecutionContext context);

    /** 判断该工具是否为只读操作（用于权限检查） */
    public boolean isReadOnly(T input) {
        return false;
    }

    /**
     * 生成 OpenAI function calling 格式的 tool schema。
     */
    public Map<String, Object> toFunctionSchema() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", getName());
        function.put("description", getDescription());
        function.put("parameters", buildParametersSchema());
        return Map.of("type", "function", "function", function);
    }

    /**
     * 基于 InputClass 的字段反射生成 JSON Schema。
     * <p>
     * 优先读取 {@link ToolParam} 注解获取 description、enum、required 等元信息；
     * 若无注解则回退到基于 @NotNull/@NotBlank 的必填检测。
     */
    protected Map<String, Object> buildParametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        Class<T> inputClass = getInputClass();
        if (inputClass != null) {
            for (Field field : inputClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;

                String fieldName = field.getName();
                Map<String, Object> fieldSchema = new LinkedHashMap<>();
                fieldSchema.put("type", mapJavaTypeToJsonType(field.getType()));

                // 数组/列表的 items 声明
                if (field.getType().isArray()) {
                    fieldSchema.put("items", Map.of("type", mapJavaTypeToJsonType(field.getType().getComponentType())));
                } else if (List.class.isAssignableFrom(field.getType())) {
                    fieldSchema.put("items", Map.of("type", "string"));
                }

                // 读取 @ToolParam 注解
                ToolParam toolParam = field.getAnnotation(ToolParam.class);
                if (toolParam != null) {
                    if (!toolParam.description().isEmpty()) {
                        fieldSchema.put("description", toolParam.description());
                    }
                    if (toolParam.enumValues().length > 0) {
                        fieldSchema.put("enum", List.of(toolParam.enumValues()));
                    }
                    if (!toolParam.defaultValue().isEmpty()) {
                        fieldSchema.put("default", toolParam.defaultValue());
                    }
                    if (toolParam.required()) {
                        required.add(fieldName);
                    }
                }

                properties.put(fieldName, fieldSchema);

                // 若 @ToolParam 未标记 required，回退检测 @NotNull/@NotBlank
                if (toolParam == null || !toolParam.required()) {
                    if (isRequiredByValidationAnnotation(field)) {
                        if (!required.contains(fieldName)) {
                            required.add(fieldName);
                        }
                    }
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private boolean isRequiredByValidationAnnotation(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            String name = annotation.annotationType().getSimpleName();
            if ("NotBlank".equals(name) || "NotNull".equals(name) || "NotEmpty".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String mapJavaTypeToJsonType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class) return "integer";
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type.isArray() || List.class.isAssignableFrom(type)) return "array";
        return "object";
    }
}
