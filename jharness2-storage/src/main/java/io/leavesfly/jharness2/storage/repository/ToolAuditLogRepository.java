package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.ToolAuditLogEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ToolAuditLogRepository extends CrudRepository<ToolAuditLogEntity, String> {

    @Query("SELECT * FROM tool_audit_logs WHERE user_id = :userId AND executed_at >= :from AND executed_at <= :to ORDER BY executed_at DESC LIMIT :limit")
    List<ToolAuditLogEntity> findByUserIdAndTimeRange(@Param("userId") String userId,
                                                      @Param("from") Instant from,
                                                      @Param("to") Instant to,
                                                      @Param("limit") int limit);

    @Query("SELECT * FROM tool_audit_logs WHERE user_id = :userId AND session_id = :sessionId ORDER BY executed_at ASC")
    List<ToolAuditLogEntity> findByUserIdAndSessionId(@Param("userId") String userId,
                                                      @Param("sessionId") String sessionId);

    @Modifying
    @Query("DELETE FROM tool_audit_logs WHERE executed_at < :before")
    void deleteByExecutedAtBefore(@Param("before") Instant before);

    @Query("SELECT COUNT(*) FROM tool_audit_logs WHERE executed_at < :before")
    long countByExecutedAtBefore(@Param("before") Instant before);
}
