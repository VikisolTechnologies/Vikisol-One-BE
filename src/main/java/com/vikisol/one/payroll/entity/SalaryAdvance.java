package com.vikisol.one.payroll.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "salary_advances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SalaryAdvance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate requestDate;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AdvanceStatus status = AdvanceStatus.PENDING;

    private UUID approvedById;

    private int emiMonths;
    private BigDecimal emiAmount;
    private BigDecimal remainingAmount;

    public enum AdvanceStatus {
        PENDING, APPROVED, REJECTED, DISBURSED
    }
}
