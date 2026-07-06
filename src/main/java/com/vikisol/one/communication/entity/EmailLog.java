package com.vikisol.one.communication.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// Brand-new table (no existing rows), so NOT NULL columns are fine here.
@Entity
@Table(name = "email_logs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(callSuper = true)
public class EmailLog extends BaseEntity {

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private UUID relatedEmployeeId;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public enum Category {
        OFFER, INTERVIEW, JOINING, WELCOME, EXIT, REMINDER, OTHER
    }

    public enum Status {
        SENT, FAILED, RETRIED
    }
}
