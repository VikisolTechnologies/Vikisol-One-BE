package com.vikisol.one.employee.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

// Movement history for an existing active employee (department/manager/location/cost center/
// business unit changes) - distinct from Offboarding, which is exit-only. Brand-new table, so
// (unlike Employee/Asset) normal NOT NULL columns are fine since it starts empty.
@Entity
@Table(name = "employee_transfers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmployeeTransfer extends BaseEntity {

    @Column(nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferType transferType;

    private String previousValue;

    @Column(nullable = false)
    private String newValue;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private UUID initiatedById;

    public enum TransferType {
        DEPARTMENT, REPORTING_MANAGER, LOCATION, COST_CENTER, BUSINESS_UNIT
    }
}
