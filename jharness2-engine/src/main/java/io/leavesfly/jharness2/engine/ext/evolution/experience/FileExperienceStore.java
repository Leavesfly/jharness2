package io.leavesfly.jharness2.engine.ext.evolution.experience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.HashSet;

/**
 * 基于文件系统的经验存储实现。
 * <p>
 * 存储结构：
 * <pre>
 *   {workspace}/.jharness2/evolution/experiences/
 *   ├── index.json          (倒排索引 keyword → experience_id)
 *   ├── {id-1}.json
 *   ├── {id-2}.json
 *   └── ...
 * </pre>
 */
public class FileExperienceStore implements ExperienceStore {

    private static final Logger logger = LoggerFactory.getLogger(FileExperienceStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Path storageDir;
    private final int maxPerUser;

    /** 内存倒排索引：keyword → Set<experienceId> */
    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();

    /** 内存中的经验缓存：id → Experience */
    private final Map<String, Experience> cache = new ConcurrentHashMap<>();

    public FileExperienceStore(Path workspace, int maxPerUser) {
        this.storageDir = workspace.resolve(".jharness2/evolution/experiences");
        this.maxPerUser = maxPerUser;
        ensureDirectory();
        loadAll();
    }

    @Override
    public void save(Experience experience) {
        // 淘汰策略：超过上限时删除最旧且低分的经验
        if (cache.size() >= maxPerUser) {
            evictLowest(experience.getUserId());
        }

        cache.put(experience.getId(), experience);
        indexExperience(experience);
        persistExperience(experience);
        persistIndex();
        logger.debug("Saved experience: id={}, task={}", experience.getId(), experience.getTaskPattern());
    }

    @Override
    public List<Experience> search(String userId, String query, int topK) {
        Set<String> queryTokens = tokenize(query);
        Map<String, Integer> scoreMap = new HashMap<>();

        for (String token : queryTokens) {
            Set<String> matchedIds = invertedIndex.get(token.toLowerCase());
            if (matchedIds != null) {
                for (String id : matchedIds) {
                    scoreMap.merge(id, 1, Integer::sum);
                }
            }
        }

        return scoreMap.entrySet().stream()
                .filter(entry -> {
                    Experience exp = cache.get(entry.getKey());
                    return exp != null && exp.getUserId().equals(userId);
                })
                .sorted((a, b) -> {
                    int scoreDiff = b.getValue() - a.getValue();
                    if (scoreDiff != 0) return scoreDiff;
                    // 同分时优先成功经验，再按时间倒序
                    Experience ea = cache.get(a.getKey());
                    Experience eb = cache.get(b.getKey());
                    if (ea.isSuccess() != eb.isSuccess()) return ea.isSuccess() ? -1 : 1;
                    return eb.getCreatedAt().compareTo(ea.getCreatedAt());
                })
                .limit(topK)
                .map(entry -> cache.get(entry.getKey()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Experience> getRecent(String userId, int limit) {
        return cache.values().stream()
                .filter(exp -> exp.getUserId().equals(userId))
                .sorted(Comparator.comparing(Experience::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String experienceId) {
        Experience removed = cache.remove(experienceId);
        if (removed != null) {
            removeFromIndex(removed);
            deleteFile(experienceId);
            persistIndex();
            logger.debug("Deleted experience: {}", experienceId);
        }
    }

    @Override
    public void updateRelevanceScore(String experienceId, float score) {
        Experience exp = cache.get(experienceId);
        if (exp != null) {
            exp.setRelevanceScore(score);
            persistExperience(exp);
        }
    }

    @Override
    public int count(String userId) {
        return (int) cache.values().stream()
                .filter(exp -> exp.getUserId().equals(userId))
                .count();
    }

    // --- 内部方法 ---

    private void ensureDirectory() {
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            logger.error("Failed to create experience storage directory: {}", storageDir, e);
        }
    }

    private void loadAll() {
        if (!Files.exists(storageDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, "*.json")) {
            for (Path file : stream) {
                if (file.getFileName().toString().equals("index.json")) continue;
                try {
                    Experience exp = MAPPER.readValue(file.toFile(), Experience.class);
                    cache.put(exp.getId(), exp);
                    indexExperience(exp);
                } catch (IOException e) {
                    logger.warn("Failed to load experience file: {}", file, e);
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to scan experience directory: {}", storageDir, e);
        }
        logger.info("Loaded {} experiences from {}", cache.size(), storageDir);
    }

    private void indexExperience(Experience exp) {
        if (exp.getKeywords() != null) {
            for (String keyword : exp.getKeywords()) {
                invertedIndex.computeIfAbsent(keyword.toLowerCase(), k -> ConcurrentHashMap.newKeySet())
                        .add(exp.getId());
            }
        }
        // 同时索引 taskPattern 中的词
        Set<String> patternTokens = tokenize(exp.getTaskPattern());
        for (String token : patternTokens) {
            invertedIndex.computeIfAbsent(token.toLowerCase(), k -> ConcurrentHashMap.newKeySet())
                    .add(exp.getId());
        }
    }

    private void removeFromIndex(Experience exp) {
        for (Set<String> ids : invertedIndex.values()) {
            ids.remove(exp.getId());
        }
        // 清理空的索引项
        invertedIndex.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void persistExperience(Experience exp) {
        Path file = storageDir.resolve(exp.getId() + ".json");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), exp);
        } catch (IOException e) {
            logger.error("Failed to persist experience: {}", exp.getId(), e);
        }
    }

    private void persistIndex() {
        Path indexFile = storageDir.resolve("index.json");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), invertedIndex);
        } catch (IOException e) {
            logger.error("Failed to persist experience index", e);
        }
    }

    private void deleteFile(String experienceId) {
        Path file = storageDir.resolve(experienceId + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warn("Failed to delete experience file: {}", file, e);
        }
    }

    private void evictLowest(String userId) {
        cache.values().stream()
                .filter(exp -> exp.getUserId().equals(userId))
                .min(Comparator.<Experience, Float>comparing(Experience::getRelevanceScore)
                        .thenComparing(Experience::getCreatedAt))
                .ifPresent(oldest -> delete(oldest.getId()));
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        Set<String> tokens = new HashSet<>();

        // 按空白和标点分词
        String[] words = text.toLowerCase().split("[\\s,;.!?，。；！？、:：()（）\\[\\]【】]+");
        for (String word : words) {
            if (word.length() > 1) {
                tokens.add(word);
            }
            // 对中文进行 bigram 切分（2 字滑动窗口）
            if (containsChinese(word) && word.length() >= 2) {
                for (int i = 0; i < word.length() - 1; i++) {
                    tokens.add(word.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    private boolean containsChinese(String text) {
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }
}
