package com.vikisol.one.attendance.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "attendances", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "date"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Attendance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate date;

    private LocalTime checkInTime;

    private LocalTime checkOutTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    @Builder.Default
    private double workingHours = 0;

    @Builder.Default
    private double overtimeHours = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AttendanceSource source = AttendanceSource.WEB;

    private String remarks;

    @Builder.Default
    private boolean isRegularized = false;

    public enum AttendanceStatus {
        PRESENT, ABSENT, HALF_DAY, ON_LEAVE, HOLIDAY, WEEKEND
    }

    public enum AttendanceSource {
        BIOMETRIC, WEB, MOBILE, MANUAL
    }
}
