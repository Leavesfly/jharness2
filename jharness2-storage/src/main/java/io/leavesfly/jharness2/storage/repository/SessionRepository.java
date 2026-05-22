package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.SessionEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends CrudRepository<SessionEntity, Long> {

    @Query("SELECT * FROM chat_sessions WHERE user_id = :userId AND session_id = :sessionId")
    Optional<SessionEntity> findByUserIdAndSessionId(@Param("userId") String userId,
                                                     @Param("sessionId") String sessionId);

    @Query("SELECT * FROM chat_sessions WHERE user_id = :userId ORDER BY updated_at DESC")
    List<SessionEntity> findByUserIdOrderByUpdatedAtDesc(@Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM chat_sessions WHERE user_id = :userId AND session_id = :sessionId")
    void deleteByUserIdAndSessionId(@Param("userId") String userId,
                                    @Param("sessionId") String sessionId);

    @Query("SELECT COUNT(*) FROM chat_sessions WHERE user_id = :userId")
    long countByUserId(@Param("userId") String userId);
}
