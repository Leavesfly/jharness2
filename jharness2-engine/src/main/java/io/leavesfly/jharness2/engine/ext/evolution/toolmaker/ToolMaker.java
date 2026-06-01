package io.leavesfly.jharness2.engine.ext.evolution.toolmaker;

import io.leavesfly.jharness2.engine.llm.LlmClient;
import io.leavesfly.jharness2.engine.llm.LlmResponse;
import io.leavesfly.jharness2.engine.message.ConversationMessage;
import io.leavesfly.jharness2.engine.tool.BaseTool;
import io.leavesfly.jharness2.engine.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 工具生成器 —— 根据 ToolSpec 调用 LLM 生成工具源码，验证后注册到 ToolRegistry。
 * <p>
 * 完整流程：
 * <ol>
 *   <li>LLM 根据 ToolSpec 生成 Java 源码（继承 BaseTool）</li>
 *   <li>ToolValidator 执行安全审查 + 编译验证</li>
 *   <li>动态类加载实例化工具</li>
 *   <li>注册到 ToolRegistry</li>
 *   <li>持久化到 workspace（供后续会话复用）</li>
 * </ol>
 */
public class ToolMaker {

    private static final Logger logger = LoggerFactory.getLogger(ToolMaker.class);

    private static final String GENERATION_PROMPT = """
            请根据以下需求生成一个 Java 工具类。该类必须：
            1. 继承 `io.leavesfly.jharness2.engine.tool.BaseTool<T>`
            2. 实现 getName()、getDescription()、getInputClass()、execute() 方法
            3. 内部定义一个 Input 类作为泛型参数 T，字段使用 @ToolParam 注解
            4. execute() 返回 CompletableFuture<ToolResult>
            5. 包名使用 `io.leavesfly.jharness2.engine.ext.evolution.toolmaker.generated`

            ## 工具需求
            - 名称: %s
            - 描述: %s
            - 参数: %s
            - 核心逻辑: %s

            ## 可用的 import
            ```
            import io.leavesfly.jharness2.engine.tool.BaseTool;
            import io.leavesfly.jharness2.engine.tool.ToolParam;
            import io.leavesfly.jharness2.engine.tool.ToolResult;
            import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
            import java.util.concurrent.CompletableFuture;
            import java.util.*;
            import java.io.*;
            import java.nio.file.*;
            ```

            ## 要求
            - 只输出 Java 源码，不要输出任何其他内容
            - 不要使用 Runtime、ProcessBuilder、System.exit 等危险 API
            - 代码要健壮，处理异常情况
            - 类名使用 PascalCase，基于工具名称生成

            请输出完整的 Java 源码：
            """;

    private final LlmClient llmClient;
    private final ToolValidator validator;
    private final ToolPersister persister;
    private final ToolRegistry toolRegistry;
    private final int maxToolsPerUser;

    public ToolMaker(LlmClient llmClient, ToolValidator validator,
                     ToolPersister persister, ToolRegistry toolRegistry, int maxToolsPerUser) {
        this.llmClient = llmClient;
        this.validator = validator;
        this.persister = persister;
        this.toolRegistry = toolRegistry;
        this.maxToolsPerUser = maxToolsPerUser;
    }

    /**
     * 根据 ToolSpec 生成、验证并注册工具。
     *
     * @param spec 工具规格描述
     * @return 创建成功的工具实例，失败返回 empty 并附带错误信息
     */
    public ToolCreationResult create(ToolSpec spec) {
        // 检查是否已存在同名工具
        if (toolRegistry.has(spec.getToolName())) {
            return ToolCreationResult.failure("Tool '" + spec.getToolName() + "' already exists");
        }

        // 检查自定义工具数量限制
        int existingCount = persister.countUserTools();
        if (existingCount >= maxToolsPerUser) {
            return ToolCreationResult.failure(
                    "Custom tool limit reached (" + maxToolsPerUser + "). Delete unused tools first.");
        }

        // Step 1: LLM 生成源码
        String sourceCode = generateSourceCode(spec);
        if (sourceCode == null || sourceCode.isBlank()) {
            return ToolCreationResult.failure("LLM failed to generate tool source code");
        }

        // Step 2: 提取类信息
        String className = ToolValidator.extractClassName(sourceCode);
        String packageName = ToolValidator.extractPackageName(sourceCode);
        String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

        if (className.isEmpty()) {
            return ToolCreationResult.failure("Could not extract class name from generated source code");
        }

        // Step 3: 验证（安全审查 + 编译）
        Path outputDir = persister.getToolOutputDir(spec.getToolName());
        ValidationResult validation = validator.validate(sourceCode, fullyQualifiedName, outputDir);
        if (!validation.isValid()) {
            return ToolCreationResult.failure("Validation failed: " + validation.getMessage());
        }

        // Step 4: 动态加载并实例化
        Optional<BaseTool<?>> toolInstance = loadAndInstantiate(fullyQualifiedName, outputDir);
        if (toolInstance.isEmpty()) {
            return ToolCreationResult.failure("Failed to load compiled tool class: " + fullyQualifiedName);
        }

        // Step 5: 注册到 ToolRegistry
        BaseTool<?> tool = toolInstance.get();
        toolRegistry.register(tool);

        // Step 6: 持久化
        persister.save(spec.getToolName(), sourceCode, spec);

        logger.info("Tool created successfully: name={}, class={}", spec.getToolName(), fullyQualifiedName);
        return ToolCreationResult.success(tool, sourceCode);
    }

    private String generateSourceCode(ToolSpec spec) {
        String paramsDescription = spec.getParameters() != null
                ? spec.getParameters().stream()
                    .map(p -> p.getName() + " (" + p.getType() + "): " + p.getDescription())
                    .collect(Collectors.joining("\n  - ", "  - ", ""))
                : "无参数";

        String prompt = String.format(GENERATION_PROMPT,
                spec.getToolName(), spec.getDescription(),
                paramsDescription, spec.getLogic());

        List<ConversationMessage> messages = List.of(
                ConversationMessage.system("你是一个 Java 代码生成器。只输出纯 Java 源码，不要输出其他内容。"),
                ConversationMessage.user(prompt)
        );

        LlmResponse response = llmClient.chatStream(messages, null, event -> {});
        if (response == null || response.getContent() == null) {
            return null;
        }

        return extractJavaSource(response.getContent());
    }

    @SuppressWarnings("unchecked")
    private Optional<BaseTool<?>> loadAndInstantiate(String className, Path classDir) {
        try {
            URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{classDir.toUri().toURL()},
                    getClass().getClassLoader()
            );

            Class<?> clazz = classLoader.loadClass(className);
            if (!BaseTool.class.isAssignableFrom(clazz)) {
                logger.error("Generated class {} does not extend BaseTool", className);
                return Optional.empty();
            }

            BaseTool<?> instance = (BaseTool<?>) clazz.getDeclaredConstructor().newInstance();
            return Optional.of(instance);

        } catch (Exception e) {
            logger.error("Failed to load and instantiate tool class {}: {}", className, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从 LLM 输出中提取 Java 源码（处理 markdown 代码块）。
     */
    private String extractJavaSource(String text) {
        // 尝试提取 ```java ... ``` 代码块
        int startMarker = text.indexOf("```java");
        if (startMarker != -1) {
            int contentStart = text.indexOf('\n', startMarker) + 1;
            int endMarker = text.indexOf("```", contentStart);
            if (endMarker != -1) {
                return text.substring(contentStart, endMarker).trim();
            }
        }

        // 尝试 ``` ... ```
        startMarker = text.indexOf("```");
        if (startMarker != -1) {
            int contentStart = text.indexOf('\n', startMarker) + 1;
            int endMarker = text.indexOf("```", contentStart);
            if (endMarker != -1) {
                return text.substring(contentStart, endMarker).trim();
            }
        }

        // 如果没有代码块标记，检查是否以 package 开头
        String trimmed = text.trim();
        if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
            return trimmed;
        }

        return trimmed;
    }
}
