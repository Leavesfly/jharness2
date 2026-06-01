package io.leavesfly.jharness2.engine.ext.evolution.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 策略指令存储 —— 管理进化指令的版本化持久化与读取。
 * <p>
 * 存储结构：
 * <pre>
 *   {workspace}/.jharness2/evolution/strategy/directives/
 *   ├── v1.json
 *   ├── v2.json
 *   └── current.json  (当前生效的指令副本)
 * </pre>
 */
public class DirectiveStore {

    private static final Logger logger = LoggerFactory.getLogger(DirectiveStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Path directivesDir;
    private volatile EvolutionDirective currentDirective;

    public DirectiveStore(Path workspace) {
        this.directivesDir = workspace.resolve(".jharness2/evolution/strategy/directives");
        ensureDirectory();
        loadCurrent();
    }

    /**
     * 保存新版本指令并设为当前生效。
     */
    public void save(EvolutionDirective directive) {
        try {
            // 保存版本文件
            Path versionFile = directivesDir.resolve("v" + directive.getVersion() + ".json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(versionFile.toFile(), directive);

            // 更新 current
            Path currentFile = directivesDir.resolve("current.json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(currentFile.toFile(), directive);

            this.currentDirective = directive;
            logger.info("Saved directive v{}: {}", directive.getVersion(),
                    truncate(directive.getPromptAddendum(), 80));
        } catch (IOException e) {
            logger.error("Failed to save directive v{}: {}", directive.getVersion(), e.getMessage());
        }
    }

    /**
     * 获取当前生效的指令。
     */
    public Optional<EvolutionDirective> getCurrent() {
        return Optional.ofNullable(currentDirective);
    }

    /**
     * 回滚到指定版本。
     */
    public boolean rollback(int targetVersion) {
        Path versionFile = directivesDir.resolve("v" + targetVersion + ".json");
        if (!Files.exists(versionFile)) {
            logger.warn("Cannot rollback: directive v{} not found", targetVersion);
            return false;
        }

        try {
            EvolutionDirective directive = MAPPER.readValue(versionFile.toFile(), EvolutionDirective.class);
            Path currentFile = directivesDir.resolve("current.json");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(currentFile.toFile(), directive);
            this.currentDirective = directive;
            logger.info("Rolled back to directive v{}", targetVersion);
            return true;
        } catch (IOException e) {
            logger.error("Failed to rollback to v{}: {}", targetVersion, e.getMessage());
            return false;
        }
    }

    /**
     * 获取当前最高版本号。
     */
    public int getLatestVersion() {
        if (currentDirective != null) return currentDirective.getVersion();
        return 0;
    }

    /**
     * 获取所有历史版本号。
     */
    public List<Integer> listVersions() {
        List<Integer> versions = new ArrayList<>();
        if (!Files.exists(directivesDir)) return versions;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directivesDir, "v*.json")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                try {
                    int version = Integer.parseInt(name.substring(1, name.indexOf('.')));
                    versions.add(version);
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            logger.warn("Failed to list directive versions: {}", e.getMessage());
        }

        versions.sort(Integer::compareTo);
        return versions;
    }

    /**
     * 更新当前指令的有效性评分。
     */
    public void updateEffectivenessScore(float score) {
        if (currentDirective != null) {
            currentDirective.setEffectivenessScore(score);
            save(currentDirective);
        }
    }

    private void loadCurrent() {
        Path currentFile = directivesDir.resolve("current.json");
        if (Files.exists(currentFile)) {
            try {
                this.currentDirective = MAPPER.readValue(currentFile.toFile(), EvolutionDirective.class);
                logger.debug("Loaded current directive v{}", currentDirective.getVersion());
            } catch (IOException e) {
                logger.warn("Failed to load current directive: {}", e.getMessage());
            }
        }
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(directivesDir);
        } catch (IOException e) {
            logger.error("Failed to create directives directory: {}", directivesDir, e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
