package io.leavesfly.jharness2.core.engine.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class SkillRegistry {

    private static final Logger logger = LoggerFactory.getLogger(SkillRegistry.class);
    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();

    public void register(SkillDefinition skill) {
        skills.put(skill.getName(), skill);
        logger.debug("Registered skill: {}", skill.getName());
    }

    public void loadFromDirectory(Path directory) {
        if (!Files.isDirectory(directory)) return;
        try (Stream<Path> paths = Files.walk(directory, 1)) {
            paths.filter(p -> p.toString().endsWith(".md"))
                    .forEach(this::loadSkillFile);
        } catch (IOException e) {
            logger.warn("Failed to scan skill directory: {}", directory, e);
        }
    }

    private void loadSkillFile(Path file) {
        try {
            String content = Files.readString(file);
            SkillDefinition skill = parseMarkdownSkill(file.getFileName().toString(), content);
            if (skill != null) {
                register(skill);
            }
        } catch (IOException e) {
            logger.warn("Failed to load skill file: {}", file, e);
        }
    }

    private SkillDefinition parseMarkdownSkill(String filename, String content) {
        String name = filename.replace(".md", "");
        String description = name;
        String body = content;
        List<String> tags = new ArrayList<>();
        Map<String, String> metadata = new HashMap<>();

        // Parse YAML frontmatter
        if (content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3);
            if (endIdx > 0) {
                String frontmatter = content.substring(3, endIdx).trim();
                body = content.substring(endIdx + 3).trim();
                for (String line : frontmatter.split("\n")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        switch (key) {
                            case "name" -> name = value;
                            case "description" -> description = value;
                            case "tags" -> tags = Arrays.asList(value.split(",\\s*"));
                            default -> metadata.put(key, value);
                        }
                    }
                }
            }
        }
        return new SkillDefinition(name, description, body, tags, metadata);
    }

    public Optional<SkillDefinition> get(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public Collection<SkillDefinition> getAll() {
        return Collections.unmodifiableCollection(skills.values());
    }

    public String buildSystemPromptSection() {
        if (skills.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## Available Skills\n");
        for (SkillDefinition skill : skills.values()) {
            sb.append("- **").append(skill.getName()).append("**: ").append(skill.getDescription()).append("\n");
        }
        return sb.toString();
    }

    public int size() {
        return skills.size();
    }

    /**
     * 加载包含项目技能的注册表（三层合并：内置 + 用户 + 项目）。
     */
    public static SkillRegistry withProject(Path projectDir) {
        return SkillLoader.loadAll(projectDir);
    }
}
