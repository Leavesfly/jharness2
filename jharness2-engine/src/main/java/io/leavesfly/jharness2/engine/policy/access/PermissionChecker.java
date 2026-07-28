package io.leavesfly.jharness2.engine.policy.access;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

public class PermissionChecker {

    private final PermissionMode mode;
    private final Set<String> allowedTools = new CopyOnWriteArraySet<>();
    private final Set<String> deniedTools = new CopyOnWriteArraySet<>();
    private final List<PathRule> pathRules = new CopyOnWriteArrayList<>();
    private final List<String> deniedCommandPatterns = new CopyOnWriteArrayList<>();
    /** shell 命令中相对路径的解析基准（通常为用户 workspace），为 null 时跳过命令路径校验 */
    private volatile Path commandBaseDir;

    public PermissionChecker(PermissionMode mode) {
        this.mode = mode;
    }

    /**
     * 设置命令内路径的解析基准目录。设置后 shell 命令中出现的绝对路径、
     * {@code ../} 穿越路径、{@code ~}/{@code $HOME} 展开都会按 {@link #isPathAllowed(Path)} 校验，
     * 防止多用户场景下借 shell 命令读写其他用户的 workspace。
     */
    public void setCommandBaseDir(Path commandBaseDir) {
        this.commandBaseDir = commandBaseDir != null ? commandBaseDir.toAbsolutePath().normalize() : null;
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

    /**
     * 路径判定：按“最具体规则优先”（pattern 字面前缀最长）决定，同等具体度下拒绝优先。
     * <p>
     * 否则 {@code workspace/**=allow} + {@code /**=deny} 这类典型配置会因“后匹配者胜”
     * 而把 workspace 内的路径也全部拒绝。
     */
    public boolean isPathAllowed(Path path) {
        if (mode == PermissionMode.PERMISSIVE) return true;
        String pathStr = path.toAbsolutePath().normalize().toString();
        PathRule best = null;
        for (PathRule rule : pathRules) {
            if (!rule.matches(pathStr)) continue;
            if (best == null
                    || rule.specificity() > best.specificity()
                    || (rule.specificity() == best.specificity() && !rule.isAllow())) {
                best = rule;
            }
        }
        if (best != null) return best.isAllow();
        return mode != PermissionMode.STRICT;
    }

    public boolean isCommandAllowed(String command) {
        if (mode == PermissionMode.PERMISSIVE) return true;
        if (isSegmentDenied(command)) return false;
        // 按 shell 连接符分段逐段匹配，防止 "echo ok && sudo ..." 借前缀绕过前缀型黑名单
        for (String segment : command.split("&&|\\|\\||[;|\\n]")) {
            String trimmed = segment.trim();
            if (!trimmed.isEmpty() && isSegmentDenied(trimmed)) return false;
        }
        return areCommandPathsAllowed(command);
    }

    /**
     * 校验命令中出现的路径是否都在允许范围内。
     * <p>
     * 这是纵深防御的一环（拦住 {@code cat ../other-user/x}、{@code cat /etc/passwd} 这类直接越界），
     * 无法覆盖所有 shell 语法变形，不能替代 OS 级沙箱隔离。
     */
    private boolean areCommandPathsAllowed(String command) {
        Path base = this.commandBaseDir;
        if (base == null) return true;
        for (String token : command.split("[\\s;|&<>()]+")) {
            String candidate = stripQuotes(token);
            if (!looksLikePath(candidate)) continue;
            if (candidate.startsWith("~") || candidate.contains("$HOME")) return false;
            Path resolved;
            try {
                resolved = base.resolve(candidate).toAbsolutePath().normalize();
            } catch (Exception e) {
                // 无法解析的路径按拒绝处理，避免异常成为绕过点
                return false;
            }
            if (!isPathAllowed(resolved)) return false;
        }
        return true;
    }

    private static String stripQuotes(String token) {
        String t = token.trim();
        while (t.length() >= 2
                && ((t.charAt(0) == '\'' && t.charAt(t.length() - 1) == '\'')
                || (t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"'))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        // 去掉 --flag=/path 形式的前缀，只留路径部分
        int eq = t.indexOf('=');
        if (t.startsWith("-") && eq >= 0) {
            t = t.substring(eq + 1);
        }
        return t;
    }

    /**
     * 仅把明确的绝对路径、穿越路径和家目录引用视为待校验路径，
     * 避免把 URL、正则（如 {@code s/a/b/}）误判成路径造成大量误杀。
     */
    private static boolean looksLikePath(String token) {
        if (token.isEmpty() || token.contains("://")) return false;
        return token.startsWith("/")
                || token.startsWith("./")
                || token.startsWith("../")
                || token.equals("..")
                || token.contains("/../")
                || token.endsWith("/..")
                || token.startsWith("~")
                || token.contains("$HOME");
    }

    private boolean isSegmentDenied(String segment) {
        for (String pattern : deniedCommandPatterns) {
            if (matchGlob(pattern, segment)) return true;
        }
        return false;
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
        // 逐字符转义，避免黑名单中的正则元字符(如 fork bomb 的括号)导致编译失败或误匹配
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return input.matches(regex.toString());
    }

    public static class PathRule {
        private final String pattern;
        private final boolean allow;
        private final int specificity;
    
        public PathRule(String pattern, boolean allow) {
            this.pattern = pattern;
            this.allow = allow;
            this.specificity = literalPrefixLength(pattern);
        }
    
        public boolean matches(String path) {
            String regex = pattern.replace(".", "\\.").replace("**", "\u00a7\u00a7")
                    .replace("*", "[^/]*").replace("\u00a7\u00a7", ".*");
            return path.matches(regex);
        }
    
        public boolean isAllow() { return allow; }
    
        /** 规则具体度：通配符之前的字面前缀长度，越长越具体 */
        int specificity() { return specificity; }
    
        private static int literalPrefixLength(String pattern) {
            int idx = pattern.indexOf('*');
            return idx >= 0 ? idx : pattern.length();
        }
    }
}
