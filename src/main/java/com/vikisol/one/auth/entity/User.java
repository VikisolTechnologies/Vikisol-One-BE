package com.vikisol.one.auth.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.security.RoleEnum;
import jakarta.persistence.*;
import lombok.*;

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
}
