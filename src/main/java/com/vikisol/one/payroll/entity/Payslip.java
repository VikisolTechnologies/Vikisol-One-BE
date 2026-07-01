package com.vikisol.one.payroll.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payslips", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "month", "year"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Payslip extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private int year;

    // Earnings
    private BigDecimal basicSalary;
    private BigDecimal hra;
    private BigDecimal conveyanceAllowance;
    private BigDecimal medicalAllowance;
    private BigDecimal specialAllowance;
    private BigDecimal otherEarnings;
    private BigDecimal grossEarnings;

    // Deductions
    private BigDecimal pfEmployee;
    private BigDecimal esiEmployee;
    private BigDecimal professionalTax;
    private BigDecimal tds;
    private BigDecimal lopDeduction;
    private BigDecimal otherDeductions;
    private BigDecimal totalDeductions;

    // Net
    private BigDecimal netSalary;

    // LOP
    private int lopDays;
    private int workingDays;
    private int presentDays;
    private int paidDays;

    // Employer contributions
    private BigDecimal pfEmployer;
    private BigDecimal esiEmployer;

    // Status
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PayslipStatus status = PayslipStatus.DRAFT;

    private LocalDateTime processedDate;
    private UUID approvedById;
    private LocalDateTime paidDate;
    private String transactionReference;

    public enum PayslipStatus {
        DRAFT, PROCESSED, APPROVED, PAID
    }
}
