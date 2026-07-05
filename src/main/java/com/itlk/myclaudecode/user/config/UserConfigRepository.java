package com.itlk.myclaudecode.user.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserConfigRepository extends JpaRepository<UserConfig, Long> {

    Optional<UserConfig> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
