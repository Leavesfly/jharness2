package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.MemoryEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemoryRepository extends CrudRepository<MemoryEntity, Long> {

    @Query("SELECT * FROM user_memories WHERE user_id = :userId AND project = :project")
    List<MemoryEntity> findByUserIdAndProject(@Param("userId") String userId,
                                              @Param("project") String project);

    @Query("SELECT * FROM user_memories WHERE user_id = :userId AND project = :project AND title = :title")
    Optional<MemoryEntity> findByUserIdAndProjectAndTitle(@Param("userId") String userId,
                                                          @Param("project") String project,
                                                          @Param("title") String title);

    @Query("SELECT * FROM user_memories WHERE user_id = :userId AND project = :project AND category = :category")
    List<MemoryEntity> findByUserIdAndProjectAndCategory(@Param("userId") String userId,
                                                         @Param("project") String project,
                                                         @Param("category") String category);

    @Modifying
    @Query("DELETE FROM user_memories WHERE user_id = :userId AND project = :project AND title = :title")
    void deleteByUserIdAndProjectAndTitle(@Param("userId") String userId,
                                          @Param("project") String project,
                                          @Param("title") String title);

    @Query("SELECT * FROM user_memories WHERE user_id = :userId AND project = :project " +
           "AND (LOWER(title) LIKE CONCAT('%', LOWER(:keyword), '%') " +
           "OR LOWER(content) LIKE CONCAT('%', LOWER(:keyword), '%'))")
    List<MemoryEntity> searchByKeyword(@Param("userId") String userId,
                                       @Param("project") String project,
                                       @Param("keyword") String keyword);
}
