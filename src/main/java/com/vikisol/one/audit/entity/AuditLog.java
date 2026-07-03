package com.vikisol.one.audit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String target;

    @Column(columnDefinition = "TEXT")
    private String details;

    private String performedByEmail;

    private String performedByName;

    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
