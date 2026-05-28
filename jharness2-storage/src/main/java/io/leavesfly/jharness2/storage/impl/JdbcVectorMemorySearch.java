package io.leavesfly.jharness2.storage.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.leavesfly.jharness2.core.spi.EmbeddingService;
import io.leavesfly.jharness2.core.spi.VectorMemorySearch;
import io.leavesfly.jharness2.core.spi.VectorSearchResult;
import io.leavesfly.jharness2.storage.entity.MemoryEmbeddingEntity;
import io.leavesfly.jharness2.storage.repository.MemoryEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 JDBC 的向量语义搜索实现。
 * <p>
 * 将 embedding 以 JSON 数组格式存储在普通列中，搜索时全量加载用户向量后在内存做余弦相似度。
 * 适用于中小规模（单用户 < 10k 条记忆）。生产环境可替换为 pgvector 原生实现。
 * <p>
 * 仅在 EmbeddingService Bean 可用时激活。
 */
@Service
@ConditionalOnBean(EmbeddingService.class)
public class JdbcVectorMemorySearch implements VectorMemorySearch {

    private static final Logger logger = LoggerFactory.getLogger(JdbcVectorMemorySearch.class);
    private static final int SNIPPET_MAX_LENGTH = 200;

    private final MemoryEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public JdbcVectorMemorySearch(MemoryEmbeddingRepository embeddingRepository,
                                   EmbeddingService embeddingService,
                                   ObjectMapper objectMapper) {
        this.embeddingRepository = embeddingRepository;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void index(String userId, String project, String memoryId, String content) {
        float[] vector = embeddingService.embed(content);
        String embeddingJson = serializeVector(vector);

        // upsert: 先删再插
        embeddingRepository.deleteByMemoryId(memoryId);

        MemoryEmbeddingEntity entity = new MemoryEmbeddingEntity();
        entity.setMemoryId(memoryId);
        entity.setUserId(userId);
        entity.setProject(project);
        entity.setContentSnippet(truncate(content, SNIPPET_MAX_LENGTH));
        entity.setEmbedding(embeddingJson);
        entity.setCreatedAt(Instant.now());
        embeddingRepository.save(entity);

        logger.debug("Indexed memory embedding: memoryId={}, userId={}, dims={}", memoryId, userId, vector.length);
    }

    @Override
    public List<VectorSearchResult> search(String userId, String project, String query, int topK) {
        float[] queryVector = embeddingService.embed(query);
        List<MemoryEmbeddingEntity> candidates = embeddingRepository.findByUserIdAndProject(userId, project);

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 计算余弦相似度并排序
        return candidates.stream()
                .map(entity -> {
                    float[] storedVector = deserializeVector(entity.getEmbedding());
                    double score = cosineSimilarity(queryVector, storedVector);
                    return new VectorSearchResult(entity.getMemoryId(), score, entity.getContentSnippet());
                })
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String memoryId) {
        embeddingRepository.deleteByMemoryId(memoryId);
        logger.debug("Deleted memory embedding: memoryId={}", memoryId);
    }

    @Override
    public void rebuildIndex(String userId, String project) {
        embeddingRepository.deleteByUserIdAndProject(userId, project);
        logger.info("Cleared all embeddings for userId={}, project={}. Re-indexing should be triggered externally.",
                userId, project);
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length || vectorA.length == 0) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0.0 ? 0.0 : dotProduct / denominator;
    }

    private String serializeVector(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize embedding vector", e);
        }
    }

    private float[] deserializeVector(String json) {
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            logger.warn("Failed to deserialize embedding vector: {}", e.getMessage());
            return new float[0];
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
