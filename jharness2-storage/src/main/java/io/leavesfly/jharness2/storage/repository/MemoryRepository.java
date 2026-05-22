package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.MemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemoryRepository extends JpaRepository<MemoryEntity, Long> {
    List<MemoryEntity> findByUserIdAndProject(String userId, String project);
    Optional<MemoryEntity> findByUserIdAndProjectAndTitle(String userId, String project, String title);
    List<MemoryEntity> findByUserIdAndProjectAndCategory(String userId, String project, String category);
    void deleteByUserIdAndProjectAndTitle(String userId, String project, String title);

    @Query("SELECT m FROM MemoryEntity m WHERE m.userId = :userId AND m.project = :project " +
           "AND (LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<MemoryEntity> searchByKeyword(String userId, String project, String keyword);
}
