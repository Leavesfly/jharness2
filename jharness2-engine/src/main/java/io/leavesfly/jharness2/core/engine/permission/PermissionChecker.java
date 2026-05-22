package io.leavesfly.jharness2.core.engine.permission;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class PermissionChecker {

    private final PermissionMode mode;
    private final Set<String> allowedTools = new CopyOnWriteArraySet<>();
    private final Set<String> deniedTools = new CopyOnWriteArraySet<>();
    private final List<PathRule> pathRules = new CopyOnWriteArrayList<>();
    private final List<String> deniedCommandPatterns = new CopyOnWriteArrayList<>();

    public PermissionChecker(PermissionMode mode) {
        this.mode = mode;
    }

    public void addAllowedTool(String toolName) {
        allowedTools.add(toolName);
    }

    public void addDeniedTool(String toolName) {
        deniedTools.add(toolName);
    }

    public void addPathRule(String globPattern, boolean allow) {
        pathRules.add(new PathRule(globPattern, allow));
    }

    public void addDeniedCommand(String pattern) {
        deniedCommandPatterns.add(pattern);
    }

    public boolean isToolAllowed(String toolName) {
        if (mode == PermissionMode.PERMISSIVE) return true;
        if (deniedTools.contains(toolName)) return false;
        if (!allowedTools.isEmpty() && !allowedTools.contains(toolName)) return false;
        return true;
    }

    public boolean isPathAllowed(Path path) {
        if (mode == PermissionMode.PERMISSIVE) return true;
        String pathStr = path.toAbsolutePath().normalize().toString();
        Boolean lastMatch = null;
        for (PathRule rule : pathRules) {
            if (rule.matches(pathStr)) {
                lastMatch = rule.isAllow();
            }
        }
        if (lastMatch != null) return lastMatch;
        return mode != PermissionMode.STRICT;
    }

    public boolean isCommandAllowed(String command) {
        if (mode == PermissionMode.PERMISSIVE) return true;
        for (String pattern : deniedCommandPatterns) {
            if (matchGlob(pattern, command)) return false;
        }
        return true;
    }

    /**
     * 统一权限判断入口，供 ToolCallDispatcher 调用。
     */
    public boolean isAllowed(String toolName, boolean readOnly, String filePath, String command) {
        if (mode == PermissionMode.PERMISSIVE) return true;
        if (!isToolAllowed(toolName)) return false;
        if (filePath != null && !filePath.isBlank() && !isPathAllowed(Path.of(filePath))) return false;
        if (command != null && !command.isBlank() && !isCommandAllowed(command)) return false;
        return true;
    }

    public PermissionMode getMode() {
        return mode;
    }

    private boolean matchGlob(String glob, String input) {
        String regex = glob.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        return input.matches(regex);
    }

    public static class PathRule {
        private final String pattern;
        private final boolean allow;

        public PathRule(String pattern, boolean allow) {
            this.pattern = pattern;
            this.allow = allow;
        }

        public boolean matches(String path) {
            String regex = pattern.replace(".", "\\.").replace("**", "§§")
                    .replace("*", "[^/]*").replace("§§", ".*");
            return path.matches(regex);
        }

        public boolean isAllow() { return allow; }
    }
}
