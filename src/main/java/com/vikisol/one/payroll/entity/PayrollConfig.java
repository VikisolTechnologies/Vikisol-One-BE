package com.vikisol.one.payroll.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "payroll_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PayrollConfig extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String value;

    private String description;

    private String category; // TAX, PF, ESI, LOP
}
