package com.vikisol.one.communication.repository;

import com.vikisol.one.communication.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface EmailLogRepository extends JpaRepository<EmailLog, java.util.UUID> {

    @Query("""
            SELECT e FROM EmailLog e
            WHERE (:category IS NULL OR e.category = :category)
            AND (:status IS NULL OR e.status = :status)
            AND (:fromDate IS NULL OR e.sentAt >= :fromDate)
            AND (:toDate IS NULL OR e.sentAt <= :toDate)
            AND (:search IS NULL OR LOWER(e.recipientEmail) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(e.subject) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY e.sentAt DESC
            """)
    Page<EmailLog> search(@Param("category") EmailLog.Category category,
                           @Param("status") EmailLog.Status status,
                           @Param("fromDate") LocalDateTime fromDate,
                           @Param("toDate") LocalDateTime toDate,
                           @Param("search") String search,
                           Pageable pageable);
}
