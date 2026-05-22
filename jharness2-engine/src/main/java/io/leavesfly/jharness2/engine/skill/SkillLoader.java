package io.leavesfly.jharness2.engine.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 技能加载器 - 三层加载机制：
 * 1. 内置技能 (classpath resources/skills/)
 * 2. 用户技能 (~/.jharness/skills/*.md)
 * 3. 项目技能 (<cwd>/.jharness/skills/*.md)
 */
public class SkillLoader {

    private static final Logger logger = LoggerFactory.getLogger(SkillLoader.class);

    /**
     * 加载完整的技能注册表（三层合并）。
     */
    public static SkillRegistry loadAll(Path projectDir) {
        SkillRegistry registry = new SkillRegistry();

        // 1. 内置技能
        List<SkillDefinition> bundled = loadBundledSkills();
        bundled.forEach(registry::register);
        logger.debug("Loaded {} bundled skills", bundled.size());

        // 2. 用户技能
        Path userSkillsDir = getUserSkillsDir();
        List<SkillDefinition> userSkills = loadFromDirectory(userSkillsDir, "user");
        userSkills.forEach(registry::register);
        logger.debug("Loaded {} user skills", userSkills.size());

        // 3. 项目技能
        if (projectDir != null) {
            Path projectSkillsDir = projectDir.resolve(".jharness").resolve("skills");
            List<SkillDefinition> projectSkills = loadFromDirectory(projectSkillsDir, "project");
            projectSkills.forEach(registry::register);
            logger.debug("Loaded {} project skills", projectSkills.size());
        }

        return registry;
    }

    /**
     * 从 classpath 加载内置技能。
     */
    private static List<SkillDefinition> loadBundledSkills() {
        List<SkillDefinition> skills = new ArrayList<>();
        try {
            var url = SkillLoader.class.getClassLoader().getResource("skills");
            if (url == null) return skills;

            Path skillsDir = Path.of(url.toURI());
            if (!Files.isDirectory(skillsDir)) return skills;

            try (Stream<Path> entries = Files.list(skillsDir)) {
                entries.sorted().forEach(entry -> {
                    if (Files.isDirectory(entry)) {
                        Path skillFile = entry.resolve("SKILL.md");
                        if (Files.exists(skillFile)) {
                            loadSingleFile(skillFile, entry.getFileName().toString(), "bundled", skills);
                        }
                    } else if (entry.toString().endsWith(".md")) {
                        String defaultName = entry.getFileName().toString().replace(".md", "");
                        loadSingleFile(entry, defaultName, "bundled", skills);
                    }
                });
            }
        } catch (Exception e) {
            logger.warn("Failed to load bundled skills", e);
        }
        return skills;
    }

    /**
     * 从目录加载所有 .md 技能文件。
     */
    public static List<SkillDefinition> loadFromDirectory(Path dir, String source) {
        List<SkillDefinition> skills = new ArrayList<>();
        if (!Files.isDirectory(dir)) return skills;

        try (Stream<Path> paths = Files.list(dir)) {
            paths.filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .forEach(file -> {
                        String defaultName = file.getFileName().toString().replace(".md", "");
                        loadSingleFile(file, defaultName, source, skills);
                    });
        } catch (IOException e) {
            logger.warn("Failed to scan skill directory: {}", dir, e);
        }
        return skills;
    }

    private static void loadSingleFile(Path file, String defaultName, String source, List<SkillDefinition> target) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String[] parsed = parseMarkdown(defaultName, content);
            target.add(new SkillDefinition(parsed[0], parsed[1], content, List.of(), java.util.Map.of("source", source)));
        } catch (IOException e) {
            logger.warn("Failed to load skill file: {}", file, e);
        }
    }

    /**
     * 解析 Markdown 技能文件 - 支持 YAML frontmatter。
     */
    public static String[] parseMarkdown(String defaultName, String content) {
        String name = defaultName;
        String description = "";

        String[] lines = content.split("\n");
        if (lines.length > 0 && lines[0].trim().equals("---")) {
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].trim().equals("---")) {
                    for (int j = 1; j < i; j++) {
                        String line = lines[j].trim();
                        if (line.startsWith("name:")) {
                            String val = line.substring(5).trim().replaceAll("^['\"]|['\"]$", "");
                            if (!val.isEmpty()) name = val;
                        } else if (line.startsWith("description:")) {
                            String val = line.substring(12).trim().replaceAll("^['\"]|['\"]$", "");
                            if (!val.isEmpty()) description = val;
                        }
                    }
                    break;
                }
            }
        }

        if (description.isEmpty()) {
            for (String line : lines) {
                String stripped = line.trim();
                if (stripped.startsWith("# ") && name.equals(defaultName)) {
                    name = stripped.substring(2).trim();
                    continue;
                }
                if (!stripped.isEmpty() && !stripped.startsWith("---") && !stripped.startsWith("#")) {
                    description = stripped.length() > 200 ? stripped.substring(0, 200) : stripped;
                    break;
                }
            }
        }

        if (description.isEmpty()) description = "Skill: " + name;
        return new String[]{name, description};
    }

    private static Path getUserSkillsDir() {
        Path dir = Path.of(System.getProperty("user.home"), ".jharness", "skills");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }
}
