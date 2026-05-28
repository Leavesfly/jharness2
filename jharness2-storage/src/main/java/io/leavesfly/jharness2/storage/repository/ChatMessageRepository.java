package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.ChatMessageEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends CrudRepository<ChatMessageEntity, Long> {

    @Query("SELECT * FROM chat_messages WHERE user_id = :userId AND session_id = :sessionId ORDER BY seq_no ASC")
    List<ChatMessageEntity> findBySession(@Param("userId") String userId,
                                           @Param("sessionId") String sessionId);

    @Query("SELECT * FROM chat_messages WHERE user_id = :userId AND session_id = :sessionId AND role = :role ORDER BY seq_no ASC")
    List<ChatMessageEntity> findBySessionAndRole(@Param("userId") String userId,
                                                  @Param("sessionId") String sessionId,
                                                  @Param("role") String role);

    @Query("SELECT COALESCE(MAX(seq_no), -1) FROM chat_messages WHERE user_id = :userId AND session_id = :sessionId")
    int findMaxSeqNo(@Param("userId") String userId,
                     @Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM chat_messages WHERE user_id = :userId AND session_id = :sessionId")
    void deleteBySession(@Param("userId") String userId,
                          @Param("sessionId") String sessionId);
}
