package com.vikisol.one.asset.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "assets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Asset extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String assetTag;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    private String brand;

    private String model;

    private String serialNumber;

    private LocalDate purchaseDate;

    private BigDecimal purchasePrice;

    private LocalDate warrantyEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Condition condition = Condition.NEW;

    private String location;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public enum Category {
        LAPTOP, DESKTOP, MONITOR, PHONE, HEADSET, FURNITURE, VEHICLE, SOFTWARE_LICENSE, OTHER
    }

    public enum Status {
        AVAILABLE, ASSIGNED, IN_REPAIR, RETIRED, LOST
    }

    public enum Condition {
        NEW, GOOD, FAIR, POOR
    }
}
