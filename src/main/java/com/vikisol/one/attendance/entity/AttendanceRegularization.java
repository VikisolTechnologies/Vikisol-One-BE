package com.vikisol.one.attendance.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "attendance_regularizations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AttendanceRegularization extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id", nullable = false)
    private Attendance attendance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    private Attendance.AttendanceStatus originalStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Attendance.AttendanceStatus requestedStatus;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RegularizationStatus status = RegularizationStatus.PENDING;

    private UUID approvedById;

    private String approverComments;

    public enum RegularizationStatus {
        PENDING, APPROVED, REJECTED
    }
}
