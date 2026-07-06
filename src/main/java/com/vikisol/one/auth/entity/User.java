package com.vikisol.one.auth.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.security.RoleEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleEnum role;

    @Builder.Default
    private boolean enabled = true;

    @Builder.Default
    private boolean accountNonLocked = true;

    // Auth/security audit fields - all nullable with no @Builder.Default and no nullable=false, so
    // adding them to this already-populated table is a safe Hibernate ddl-auto=update migration
    // (existing rows just get NULL, never a rejected NOT-NULL ALTER TABLE).
    private Instant lastLoginAt;
    private Instant passwordChangedAt;
    private Instant lockedUntil;
    private Integer failedLoginCount;
}
