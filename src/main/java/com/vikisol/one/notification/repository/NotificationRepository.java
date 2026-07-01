package com.vikisol.one.notification.repository;

import com.vikisol.one.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);
    List<Notification> findByRecipientIdAndIsReadFalse(UUID recipientId);
    long countByRecipientIdAndIsReadFalse(UUID recipientId);
}
