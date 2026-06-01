package io.leavesfly.jharness2.engine.ext.evolution.toolmaker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.leavesfly.jharness2.engine.tool.BaseTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 工具持久化管理 —— 保存和加载用户自生成的工具。
 * <p>
 * 存储结构：
 * <pre>
 *   {workspace}/.jharness2/evolution/tools/
 *   ├── {tool-name}/
 *   │   ├── tool.json      (ToolSpec 元数据)
 *   │   ├── Source.java    (源码)
 *   │   └── *.class        (编译产物)
 *   └── ...
 * </pre>
 */
public class ToolPersister {

    private static final Logger logger = LoggerFactory.getLogger(ToolPersister.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Path toolsBaseDir;

    public ToolPersister(Path workspace) {
        this.toolsBaseDir = workspace.resolve(".jharness2/evolution/tools");
        ensureDirectory();
    }

    /**
     * 保存工具源码和元数据。
     */
    public void save(String toolName, String sourceCode, ToolSpec spec) {
        Path toolDir = toolsBaseDir.resolve(toolName);
        try {
            Files.createDirectories(toolDir);

            // 保存源码
            String className = ToolValidator.extractClassName(sourceCode);
            Path sourceFile = toolDir.resolve(className + ".java");
            Files.writeString(sourceFile, sourceCode);

            // 保存元数据
            Path metaFile = toolDir.resolve("tool.json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(metaFile.toFile(), spec);

            logger.info("Persisted tool: name={}, dir={}", toolName, toolDir);
        } catch (IOException e) {
            logger.error("Failed to persist tool {}: {}", toolName, e.getMessage());
        }
    }

    /**
     * 删除一个自定义工具。
     */
    public boolean delete(String toolName) {
        Path toolDir = toolsBaseDir.resolve(toolName);
        if (!Files.exists(toolDir)) return false;

        try (Stream<Path> files = Files.walk(toolDir)) {
            files.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException ignored) {}
                    });
            logger.info("Deleted tool: {}", toolName);
            return true;
        } catch (IOException e) {
            logger.error("Failed to delete tool {}: {}", toolName, e.getMessage());
            return false;
        }
    }

    /**
     * 加载所有已持久化的用户工具。
     *
     * @return 已加载的工具实例列表
     */
    @SuppressWarnings("unchecked")
    public List<BaseTool<?>> loadAll() {
        List<BaseTool<?>> tools = new ArrayList<>();
        if (!Files.exists(toolsBaseDir)) return tools;

        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(toolsBaseDir)) {
            for (Path toolDir : dirs) {
                if (!Files.isDirectory(toolDir)) continue;

                Optional<BaseTool<?>> tool = loadTool(toolDir);
                tool.ifPresent(tools::add);
            }
        } catch (IOException e) {
            logger.warn("Failed to scan tools directory: {}", toolsBaseDir, e);
        }

        logger.info("Loaded {} custom tools from {}", tools.size(), toolsBaseDir);
        return tools;
    }

    /**
     * 获取指定工具的编译输出目录。
     */
    public Path getToolOutputDir(String toolName) {
        return toolsBaseDir.resolve(toolName);
    }

    /**
     * 获取当前用户自定义工具数量。
     */
    public int countUserTools() {
        if (!Files.exists(toolsBaseDir)) return 0;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(toolsBaseDir)) {
            int count = 0;
            for (Path dir : dirs) {
                if (Files.isDirectory(dir)) count++;
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 列出所有自定义工具名称。
     */
    public List<String> listToolNames() {
        List<String> names = new ArrayList<>();
        if (!Files.exists(toolsBaseDir)) return names;

        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(toolsBaseDir)) {
            for (Path dir : dirs) {
                if (Files.isDirectory(dir)) {
                    names.add(dir.getFileName().toString());
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to list tools: {}", e.getMessage());
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private Optional<BaseTool<?>> loadTool(Path toolDir) {
        try {
            // 查找 .class 文件
            Path metaFile = toolDir.resolve("tool.json");
            if (!Files.exists(metaFile)) {
                logger.debug("No tool.json found in {}, skipping", toolDir);
                return Optional.empty();
            }

            // 查找源码获取类名
            Optional<Path> sourceFile = Files.list(toolDir)
                    .filter(p -> p.toString().endsWith(".java"))
                    .findFirst();

            if (sourceFile.isEmpty()) {
                return Optional.empty();
            }

            String source = Files.readString(sourceFile.get());
            String className = ToolValidator.extractClassName(source);
            String packageName = ToolValidator.extractPackageName(source);
            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            // 检查是否有编译产物
            boolean hasClassFile = Files.list(toolDir)
                    .anyMatch(p -> p.toString().endsWith(".class"));

            if (!hasClassFile) {
                logger.debug("No .class files found for tool in {}, skipping", toolDir);
                return Optional.empty();
            }

            // 动态加载
            URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{toolDir.toUri().toURL()},
                    getClass().getClassLoader()
            );

            Class<?> clazz = classLoader.loadClass(fullyQualifiedName);
            if (!BaseTool.class.isAssignableFrom(clazz)) {
                logger.warn("Class {} does not extend BaseTool, skipping", fullyQualifiedName);
                return Optional.empty();
            }

            BaseTool<?> instance = (BaseTool<?>) clazz.getDeclaredConstructor().newInstance();
            logger.debug("Loaded custom tool: {}", instance.getName());
            return Optional.of(instance);

        } catch (Exception e) {
            logger.warn("Failed to load tool from {}: {}", toolDir, e.getMessage());
            return Optional.empty();
        }
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(toolsBaseDir);
        } catch (IOException e) {
            logger.error("Failed to create tools directory: {}", toolsBaseDir, e);
        }
    }
}
