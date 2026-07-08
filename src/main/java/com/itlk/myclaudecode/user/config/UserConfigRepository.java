package com.itlk.myclaudecode.user.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserConfigRepository extends JpaRepository<UserConfig, Long> {

    Optional<UserConfig> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    List<UserConfig> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<UserConfig> findByUserIdAndIsActiveTrue(Long userId);

    Optional<UserConfig> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndChannelName(Long userId, String channelName);

    @Modifying
    @Query("UPDATE UserConfig u SET u.isActive = false WHERE u.userId = :userId")
    void deactivateAllByUserId(@Param("userId") Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
