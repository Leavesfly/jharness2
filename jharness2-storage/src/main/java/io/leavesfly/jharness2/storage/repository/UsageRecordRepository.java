package io.leavesfly.jharness2.storage.repository;

import io.leavesfly.jharness2.storage.entity.UsageRecordEntity;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UsageRecordRepository extends CrudRepository<UsageRecordEntity, Long> {

    @Query("SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM usage_records WHERE user_id = :userId AND recorded_at >= :dayStart AND recorded_at < :dayEnd")
    long sumTokensByUserIdAndDay(@Param("userId") String userId,
                                 @Param("dayStart") Instant dayStart,
                                 @Param("dayEnd") Instant dayEnd);

    @Query("SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM usage_records WHERE user_id = :userId AND recorded_at >= :monthStart AND recorded_at < :monthEnd")
    long sumTokensByUserIdAndMonth(@Param("userId") String userId,
                                   @Param("monthStart") Instant monthStart,
                                   @Param("monthEnd") Instant monthEnd);

    @Query("SELECT * FROM usage_records WHERE user_id = :userId AND recorded_at >= :fromTime AND recorded_at < :toTime ORDER BY recorded_at DESC")
    List<UsageRecordEntity> findByUserIdAndDateRange(@Param("userId") String userId,
                                                     @Param("fromTime") Instant fromTime,
                                                     @Param("toTime") Instant toTime);

    @Query("SELECT model, COALESCE(SUM(input_tokens), 0) as input_tokens, COALESCE(SUM(output_tokens), 0) as output_tokens, COUNT(*) as request_count FROM usage_records WHERE user_id = :userId AND recorded_at >= :fromTime AND recorded_at < :toTime GROUP BY model")
    List<Object[]> findModelUsageSummary(@Param("userId") String userId,
                                         @Param("fromTime") Instant fromTime,
                                         @Param("toTime") Instant toTime);

    @Modifying
    @Query("DELETE FROM usage_records WHERE recorded_at < :before")
    void deleteByRecordedAtBefore(@Param("before") Instant before);

    @Query("SELECT COUNT(*) FROM usage_records WHERE recorded_at < :before")
    long countByRecordedAtBefore(@Param("before") Instant before);
}
