package com.vikisol.one.asset.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "asset_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AssetAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate assignedDate;

    private LocalDate returnDate;

    @Column(nullable = false)
    private UUID assignedById;

    private UUID returnedById;

    @Enumerated(EnumType.STRING)
    private Asset.Condition conditionAtAssignment;

    @Enumerated(EnumType.STRING)
    private Asset.Condition conditionAtReturn;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Builder.Default
    private boolean isActive = true;
}
