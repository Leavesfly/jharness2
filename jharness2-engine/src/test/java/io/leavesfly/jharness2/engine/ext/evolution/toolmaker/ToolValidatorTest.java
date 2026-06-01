package io.leavesfly.jharness2.engine.ext.evolution.toolmaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolValidatorTest {

    @TempDir
    Path tempDir;

    private ToolValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ToolValidator(
                List.of("java.lang.reflect", "sun.misc"),
                10
        );
    }

    @Test
    void securityCheckPassesForSafeCode() {
        String safeCode = """
                package io.leavesfly.jharness2.engine.ext.evolution.toolmaker.generated;
                
                import io.leavesfly.jharness2.engine.tool.BaseTool;
                import io.leavesfly.jharness2.engine.tool.ToolResult;
                import io.leavesfly.jharness2.engine.tool.ToolExecutionContext;
                import java.util.*;
                import java.nio.file.*;
                import java.util.concurrent.CompletableFuture;
                
                public class SafeTool extends BaseTool<SafeTool.Input> {
                    public String getName() { return "safe_tool"; }
                    public String getDescription() { return "A safe tool"; }
                    public Class<Input> getInputClass() { return Input.class; }
                    public CompletableFuture<ToolResult> execute(Input input, ToolExecutionContext ctx) {
                        return CompletableFuture.completedFuture(ToolResult.success("ok"));
                    }
                    public static class Input { public String value; }
                }
                """;

        ValidationResult result = validator.checkSecurity(safeCode);
        assertTrue(result.isValid(), result.getMessage());
    }

    @Test
    void securityCheckRejectsRuntimeGetRuntime() {
        String dangerousCode = """
                package test;
                public class DangerousTool {
                    public void execute() {
                        Runtime.getRuntime().exec("rm -rf /");
                    }
                }
                """;

        ValidationResult result = validator.checkSecurity(dangerousCode);
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("Runtime.getRuntime"));
    }

    @Test
    void securityCheckRejectsProcessBuilder() {
        String code = """
                package test;
                public class BadTool {
                    public void execute() {
                        new ProcessBuilder("ls").start();
                    }
                }
                """;

        ValidationResult result = validator.checkSecurity(code);
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("ProcessBuilder"));
    }

    @Test
    void securityCheckRejectsDeniedImport() {
        String code = """
                package test;
                import java.lang.reflect.Method;
                public class ReflectTool {
                    public void execute() throws Exception {
                        Method m = String.class.getMethod("length");
                    }
                }
                """;

        ValidationResult result = validator.checkSecurity(code);
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("Denied import"));
    }

    @Test
    void securityCheckRejectsSystemExit() {
        String code = """
                package test;
                public class ExitTool {
                    public void execute() { System.exit(0); }
                }
                """;

        ValidationResult result = validator.checkSecurity(code);
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("System.exit"));
    }

    @Test
    void extractClassNameFromSource() {
        String code = "package com.example;\npublic class MyTool extends BaseTool<Input> {}";
        assertEquals("MyTool", ToolValidator.extractClassName(code));
    }

    @Test
    void extractPackageNameFromSource() {
        String code = "package com.example.tools;\npublic class MyTool {}";
        assertEquals("com.example.tools", ToolValidator.extractPackageName(code));
    }

    @Test
    void extractPackageNameReturnsEmptyForNoPackage() {
        String code = "public class NoPackageTool {}";
        assertEquals("", ToolValidator.extractPackageName(code));
    }
}
