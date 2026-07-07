package com.vikisol.one.notification.repository;

import com.vikisol.one.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);
    List<Notification> findByRecipientIdAndIsReadFalse(UUID recipientId);
    long countByRecipientIdAndIsReadFalse(UUID recipientId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    // Optional filters kept simple with COALESCE-style null checks rather than a Specification -
    // fine for this table's scale and avoids pulling in the Specification machinery for one endpoint.
    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientId = :recipientId
            AND (:category IS NULL OR n.category = :category)
            AND (:priority IS NULL OR n.priority = :priority)
            AND (:read IS NULL OR n.isRead = :read)
            AND (:archived IS NULL OR COALESCE(n.archived, false) = :archived)
            AND (:search IS NULL OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(n.message) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> search(@Param("recipientId") UUID recipientId,
                               @Param("category") String category,
                               @Param("priority") Notification.Priority priority,
                               @Param("read") Boolean read,
                               @Param("archived") Boolean archived,
                               @Param("search") String search,
                               Pageable pageable);
}
