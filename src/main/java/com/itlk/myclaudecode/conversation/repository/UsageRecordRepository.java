package com.itlk.myclaudecode.conversation.repository;

import com.itlk.myclaudecode.conversation.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    @Query("SELECT COALESCE(SUM(u.inputTokens), 0) FROM UsageRecord u WHERE u.userId = :userId")
    Long sumInputTokensByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(u.inputTokens), 0) FROM UsageRecord u WHERE u.userId = :userId AND u.createdAt > :since")
    Long sumInputTokensByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(u.outputTokens), 0) FROM UsageRecord u WHERE u.userId = :userId")
    Long sumOutputTokensByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(u.outputTokens), 0) FROM UsageRecord u WHERE u.userId = :userId AND u.createdAt > :since")
    Long sumOutputTokensByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT COALESCE(SUM(u.toolCallCount), 0) FROM UsageRecord u WHERE u.userId = :userId")
    Long sumToolCallCountByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(u.toolCallCount), 0) FROM UsageRecord u WHERE u.userId = :userId AND u.createdAt > :since")
    Long sumToolCallCountByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(u) FROM UsageRecord u WHERE u.userId = :userId")
    Long countByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(u) FROM UsageRecord u WHERE u.userId = :userId AND u.createdAt > :since")
    Long countByUserIdSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    @Modifying
    @Query("DELETE FROM UsageRecord u WHERE u.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
