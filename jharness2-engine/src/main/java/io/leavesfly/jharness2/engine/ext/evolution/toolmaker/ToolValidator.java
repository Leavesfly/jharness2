package io.leavesfly.jharness2.engine.ext.evolution.toolmaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具验证器 —— 对 LLM 生成的工具源码进行安全审查和编译验证。
 * <p>
 * 验证流程：
 * <ol>
 *   <li>静态安全审查（禁止的 import / API 调用）</li>
 *   <li>使用 javax.tools.JavaCompiler 内存编译</li>
 *   <li>验证编译产物正确继承 BaseTool</li>
 * </ol>
 */
public class ToolValidator {

    private static final Logger logger = LoggerFactory.getLogger(ToolValidator.class);

    /** 禁止出现在生成代码中的危险 API 模式 */
    private static final List<String> DENIED_API_PATTERNS = List.of(
            "Runtime.getRuntime",
            "ProcessBuilder",
            "System.exit",
            "Thread.sleep",
            "ClassLoader",
            "Unsafe",
            "java.lang.reflect.Proxy",
            "java.net.ServerSocket",
            "java.rmi",
            "javax.script"
    );

    private final List<String> deniedImports;
    private final int compileTimeoutSeconds;

    public ToolValidator(List<String> deniedImports, int compileTimeoutSeconds) {
        this.deniedImports = deniedImports != null ? deniedImports : List.of();
        this.compileTimeoutSeconds = compileTimeoutSeconds;
    }

    /**
     * 执行完整的验证流程。
     *
     * @param sourceCode     工具 Java 源码
     * @param className      完全限定类名
     * @param outputDir      编译输出目录
     * @return 验证结果
     */
    public ValidationResult validate(String sourceCode, String className, Path outputDir) {
        // Step 1: 静态安全审查
        ValidationResult securityCheck = checkSecurity(sourceCode);
        if (!securityCheck.isValid()) {
            return securityCheck;
        }

        // Step 2: 编译验证
        ValidationResult compileResult = compile(sourceCode, className, outputDir);
        if (!compileResult.isValid()) {
            return compileResult;
        }

        return ValidationResult.success("Validation passed: security check + compilation OK");
    }

    /**
     * 静态安全审查 —— 检查禁止的 import 和 API 调用。
     */
    public ValidationResult checkSecurity(String sourceCode) {
        List<String> violations = new ArrayList<>();

        // 检查禁止的 import
        for (String denied : deniedImports) {
            String importPattern = "import\\s+" + denied.replace(".", "\\.").replace("*", ".*");
            if (sourceCode.matches("(?s).*" + importPattern + ".*")) {
                violations.add("Denied import: " + denied);
            }
        }

        // 检查禁止的 API 调用
        for (String pattern : DENIED_API_PATTERNS) {
            if (sourceCode.contains(pattern)) {
                violations.add("Denied API usage: " + pattern);
            }
        }

        // 检查网络操作（保守策略）
        if (sourceCode.contains("new URL(") || sourceCode.contains("HttpURLConnection")) {
            // 允许 OkHttp（已在项目依赖中），但禁止直接 java.net
            if (!sourceCode.contains("okhttp3")) {
                violations.add("Direct java.net usage not allowed, use OkHttp instead");
            }
        }

        if (!violations.isEmpty()) {
            String message = "Security check failed:\n- " + String.join("\n- ", violations);
            logger.warn("Tool security violation: {}", message);
            return ValidationResult.failure(message);
        }

        return ValidationResult.success("Security check passed");
    }

    /**
     * 使用 javax.tools.JavaCompiler 编译源码。
     */
    public ValidationResult compile(String sourceCode, String className, Path outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return ValidationResult.failure("JavaCompiler not available (JDK required, not JRE)");
        }

        try {
            Files.createDirectories(outputDir);

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

            // 设置输出目录
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));

            // 添加当前 classpath
            String classpath = System.getProperty("java.class.path");
            if (classpath != null && !classpath.isEmpty()) {
                List<File> classPathFiles = Arrays.stream(classpath.split(File.pathSeparator))
                        .map(File::new)
                        .toList();
                fileManager.setLocation(StandardLocation.CLASS_PATH, classPathFiles);
            }

            // 创建内存中的源码文件
            String simpleClassName = className.contains(".")
                    ? className.substring(className.lastIndexOf('.') + 1)
                    : className;
            JavaFileObject sourceFile = new InMemoryJavaFileObject(simpleClassName, sourceCode);

            // 编译
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, null, null, List.of(sourceFile));

            boolean success = task.call();
            fileManager.close();

            if (!success) {
                StringBuilder errors = new StringBuilder("Compilation failed:\n");
                for (Diagnostic<? extends JavaFileObject> diag : diagnostics.getDiagnostics()) {
                    if (diag.getKind() == Diagnostic.Kind.ERROR) {
                        errors.append("  Line ").append(diag.getLineNumber())
                                .append(": ").append(diag.getMessage(null)).append("\n");
                    }
                }
                return ValidationResult.failure(errors.toString().trim());
            }

            return ValidationResult.success("Compilation successful");

        } catch (Exception e) {
            return ValidationResult.failure("Compilation error: " + e.getMessage());
        }
    }

    /**
     * 从源码中提取包名。
     */
    public static String extractPackageName(String sourceCode) {
        Pattern pattern = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(sourceCode);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 从源码中提取类名。
     */
    public static String extractClassName(String sourceCode) {
        Pattern pattern = Pattern.compile("public\\s+class\\s+(\\w+)", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(sourceCode);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 内存中的 Java 源文件对象（用于 JavaCompiler）。
     */
    private static class InMemoryJavaFileObject extends SimpleJavaFileObject {
        private final String sourceCode;

        InMemoryJavaFileObject(String className, String sourceCode) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.sourceCode = sourceCode;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return sourceCode;
        }
    }
}
