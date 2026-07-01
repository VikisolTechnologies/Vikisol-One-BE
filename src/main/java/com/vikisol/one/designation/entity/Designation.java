package com.vikisol.one.designation.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "designations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Designation extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String title;

    private int level;

    private String description;

    @Builder.Default
    private boolean isActive = true;
}
