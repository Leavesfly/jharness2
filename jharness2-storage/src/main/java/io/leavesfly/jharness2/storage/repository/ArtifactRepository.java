package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.ArtifactEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtifactRepository extends CrudRepository<ArtifactEntity, String> {

    @Query("SELECT * FROM workspace_artifacts WHERE user_id = :userId AND session_id = :sessionId ORDER BY created_at DESC")
    List<ArtifactEntity> findByUserIdAndSessionId(@Param("userId") String userId,
                                                   @Param("sessionId") String sessionId);

    @Query("SELECT * FROM workspace_artifacts WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit")
    List<ArtifactEntity> findRecentByUserId(@Param("userId") String userId,
                                             @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM workspace_artifacts WHERE id = :artifactId")
    void deleteByArtifactId(@Param("artifactId") String artifactId);
}
