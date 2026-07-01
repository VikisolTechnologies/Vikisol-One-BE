package com.vikisol.one.leave.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leave_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LeaveRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private double numberOfDays;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LeaveStatus status = LeaveStatus.PENDING;

    private UUID approvedById;

    private String approverComments;

    @Builder.Default
    private boolean isHalfDay = false;

    @Enumerated(EnumType.STRING)
    private HalfDayType halfDayType;

    @Column(nullable = false)
    private LocalDateTime appliedOn;

    public enum LeaveStatus {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    public enum HalfDayType {
        FIRST_HALF, SECOND_HALF
    }
}
