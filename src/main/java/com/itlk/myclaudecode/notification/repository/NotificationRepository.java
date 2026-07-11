package com.itlk.myclaudecode.notification.repository;

import com.itlk.myclaudecode.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderByCreatedAtDesc();

    List<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
