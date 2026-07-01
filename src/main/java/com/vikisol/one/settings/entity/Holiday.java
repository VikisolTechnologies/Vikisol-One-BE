package com.vikisol.one.settings.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "holidays", uniqueConstraints = {
        @UniqueConstraint(name = "uk_holiday_name_date", columnNames = {"name", "date"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Holiday extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HolidayType type;

    @Builder.Default
    private boolean isOptional = false;

    private int year;

    private String description;

    public enum HolidayType {
        NATIONAL, REGIONAL, OPTIONAL, COMPANY
    }
}
