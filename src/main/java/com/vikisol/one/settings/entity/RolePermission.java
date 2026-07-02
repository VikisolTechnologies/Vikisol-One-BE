package com.vikisol.one.settings.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.security.RoleEnum;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role_permissions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"role", "module"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RolePermission extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleEnum role;

    @Column(nullable = false)
    private String module;

    @Builder.Default
    @Column(nullable = false)
    private boolean canView = false;
}
