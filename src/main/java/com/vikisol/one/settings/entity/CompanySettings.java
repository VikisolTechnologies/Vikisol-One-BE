package com.vikisol.one.settings.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "company_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompanySettings extends BaseEntity {

    @Column(nullable = false, unique = true, name = "setting_key")
    private String key;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettingsCategory category;

    private String description;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DataType dataType = DataType.STRING;

    public enum SettingsCategory {
        GENERAL, LEAVE, ATTENDANCE, PAYROLL, NOTIFICATION
    }

    public enum DataType {
        STRING, NUMBER, BOOLEAN, JSON
    }
}
