package io.leavesfly.jharness2.engine.ext.skill;

import java.util.List;
import java.util.Map;

public class SkillDefinition {
    private final String name;
    private final String description;
    private final String content;
    private final List<String> tags;
    private final Map<String, String> metadata;

    public SkillDefinition(String name, String description, String content,
                           List<String> tags, Map<String, String> metadata) {
        this.name = name;
        this.description = description;
        this.content = content;
        this.tags = tags != null ? tags : List.of();
        this.metadata = metadata != null ? metadata : Map.of();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getContent() { return content; }
    public List<String> getTags() { return tags; }
    public Map<String, String> getMetadata() { return metadata; }
}
