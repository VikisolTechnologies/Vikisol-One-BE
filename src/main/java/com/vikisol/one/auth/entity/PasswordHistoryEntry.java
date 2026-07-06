package com.vikisol.one.auth.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// One row per password an employee has ever set (hashed, never plaintext) - checked against on
// every password change/reset/activation so the last N passwords (CEO/Admin-configurable via
// Authentication Settings) cannot be reused.
@Entity
@Table(name = "password_history_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PasswordHistoryEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String passwordHash;
}
