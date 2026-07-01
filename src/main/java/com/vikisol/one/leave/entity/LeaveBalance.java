package com.vikisol.one.leave.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "leave_balances", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "leave_type_id", "year"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LeaveBalance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private int year;

    @Builder.Default
    private double totalDays = 0;

    @Builder.Default
    private double usedDays = 0;

    @Builder.Default
    private double remainingDays = 0;

    @Builder.Default
    private double carryForwardDays = 0;
}
