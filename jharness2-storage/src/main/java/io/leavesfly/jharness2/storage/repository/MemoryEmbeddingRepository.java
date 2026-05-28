package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.MemoryEmbeddingEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemoryEmbeddingRepository extends CrudRepository<MemoryEmbeddingEntity, Long> {

    @Query("SELECT * FROM memory_embeddings WHERE user_id = :userId AND project = :project")
    List<MemoryEmbeddingEntity> findByUserIdAndProject(@Param("userId") String userId,
                                                       @Param("project") String project);

    Optional<MemoryEmbeddingEntity> findByMemoryId(String memoryId);

    @Modifying
    @Query("DELETE FROM memory_embeddings WHERE memory_id = :memoryId")
    void deleteByMemoryId(@Param("memoryId") String memoryId);

    @Modifying
    @Query("DELETE FROM memory_embeddings WHERE user_id = :userId AND project = :project")
    void deleteByUserIdAndProject(@Param("userId") String userId, @Param("project") String project);
}
