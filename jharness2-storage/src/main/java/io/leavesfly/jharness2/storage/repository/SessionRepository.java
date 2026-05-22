package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, Long> {
    Optional<SessionEntity> findByUserIdAndSessionId(String userId, String sessionId);
    List<SessionEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
    void deleteByUserIdAndSessionId(String userId, String sessionId);
    long countByUserId(String userId);
}
