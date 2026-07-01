package com.vikisol.one.leave.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "leave_types")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LeaveType extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private int defaultDays;

    @Builder.Default
    private boolean carryForward = false;

    @Builder.Default
    private int maxCarryForwardDays = 0;

    @Builder.Default
    private boolean isActive = true;
}
