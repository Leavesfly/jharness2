package io.leavesfly.jharness2.engine.llm;

import java.util.Map;

/**
 * LLM 响应格式约束 — 控制 LLM 输出的结构化格式。
 * <p>
 * 支持三种模式：
 * <ul>
 *   <li>{@link #text()} — 默认，自由文本输出</li>
 *   <li>{@link #json()} — 要求 JSON 输出（无 Schema 约束）</li>
 *   <li>{@link #jsonSchema(String, Map)} — 带 JSON Schema 约束的结构化输出</li>
 * </ul>
 *
 * 对应 OpenAI API 的 response_format 参数。
 */
public class ResponseFormat {

    public enum Type {
        TEXT,
        JSON_OBJECT,
        JSON_SCHEMA
    }

    private final Type type;
    private final String schemaName;
    private final Map<String, Object> jsonSchema;
    private final boolean strict;

    private ResponseFormat(Type type, String schemaName, Map<String, Object> jsonSchema, boolean strict) {
        this.type = type;
        this.schemaName = schemaName;
        this.jsonSchema = jsonSchema;
        this.strict = strict;
    }

    /** 默认文本输出 */
    public static ResponseFormat text() {
        return new ResponseFormat(Type.TEXT, null, null, false);
    }

    /** JSON 对象输出（无 Schema 约束） */
    public static ResponseFormat json() {
        return new ResponseFormat(Type.JSON_OBJECT, null, null, false);
    }

    /** 带 JSON Schema 约束的结构化输出 */
    public static ResponseFormat jsonSchema(String name, Map<String, Object> schema) {
        return new ResponseFormat(Type.JSON_SCHEMA, name, schema, true);
    }

    /** 带 JSON Schema 约束的结构化输出（可选严格模式） */
    public static ResponseFormat jsonSchema(String name, Map<String, Object> schema, boolean strict) {
        return new ResponseFormat(Type.JSON_SCHEMA, name, schema, strict);
    }

    public Type getType() { return type; }
    public String getSchemaName() { return schemaName; }
    public Map<String, Object> getJsonSchema() { return jsonSchema; }
    public boolean isStrict() { return strict; }

    /**
     * 转为 OpenAI API 格式的 response_format 参数。
     */
    public Map<String, Object> toApiFormat() {
        return switch (type) {
            case TEXT -> Map.of("type", "text");
            case JSON_OBJECT -> Map.of("type", "json_object");
            case JSON_SCHEMA -> {
                Map<String, Object> schemaBlock = new java.util.LinkedHashMap<>();
                schemaBlock.put("name", schemaName != null ? schemaName : "response");
                schemaBlock.put("strict", strict);
                if (jsonSchema != null) {
                    schemaBlock.put("schema", jsonSchema);
                }
                yield Map.of("type", "json_schema", "json_schema", schemaBlock);
            }
        };
    }
}
