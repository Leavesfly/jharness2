package io.leavesfly.jharness2.storage.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * 记忆向量索引实体 —— 存储 memory 的 embedding 向量用于语义检索。
 */
@Table("memory_embeddings")
public class MemoryEmbeddingEntity {

    @Id
    private Long id;
    private String memoryId;
    private String userId;
    private String project;
    private String contentSnippet;
    private String embedding;  // JSON 格式的 float 数组，或二进制存储
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMemoryId() { return memoryId; }
    public void setMemoryId(String memoryId) { this.memoryId = memoryId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }
    public String getContentSnippet() { return contentSnippet; }
    public void setContentSnippet(String contentSnippet) { this.contentSnippet = contentSnippet; }
    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
