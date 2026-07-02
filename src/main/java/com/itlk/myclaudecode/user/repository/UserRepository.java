package com.itlk.myclaudecode.user.repository;

import com.itlk.myclaudecode.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByAccountId(String accountId);
}
