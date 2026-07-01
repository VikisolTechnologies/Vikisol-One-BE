package com.vikisol.one.resource.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "resources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Resource extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceCategory category;

    private String fileUrl;

    private String externalLink;

    @Builder.Default
    private boolean isPublic = false;

    private UUID uploadedById;

    @Builder.Default
    private boolean isActive = true;

    public enum ResourceCategory {
        POLICY, HANDBOOK, TEMPLATE, GUIDE, LINK, OTHER
    }
}
