package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.AgentTaskEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AgentTaskRepository extends CrudRepository<AgentTaskEntity, String> {

    @Query("SELECT * FROM agent_tasks WHERE user_id = :userId AND status IN ('PENDING', 'RUNNING')")
    List<AgentTaskEntity> findActiveTasks(@Param("userId") String userId);

    @Query("SELECT * FROM agent_tasks WHERE status IN ('PENDING', 'RUNNING') AND timeout_at IS NOT NULL AND timeout_at < :before")
    List<AgentTaskEntity> findTimedOut(@Param("before") Instant before);

    @Modifying
    @Query("UPDATE agent_tasks SET status = :status, output = :output, updated_at = :updatedAt WHERE id = :taskId")
    void updateStatus(@Param("taskId") String taskId,
                      @Param("status") String status,
                      @Param("output") String output,
                      @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query("UPDATE agent_tasks SET retry_count = retry_count + 1, updated_at = :updatedAt WHERE id = :taskId")
    void incrementRetryCount(@Param("taskId") String taskId,
                             @Param("updatedAt") Instant updatedAt);
}
