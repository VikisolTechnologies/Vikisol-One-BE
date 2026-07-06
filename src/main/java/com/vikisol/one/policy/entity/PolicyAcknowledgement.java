package com.vikisol.one.policy.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// One row per (policy, employee) pair, tracking the View -> Accept flow. Brand-new table.
@Entity
@Table(name = "policy_acknowledgements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PolicyAcknowledgement extends BaseEntity {

    @Column(nullable = false)
    private UUID policyId;

    @Column(nullable = false)
    private UUID employeeId;

    private LocalDateTime viewedAt;

    // Null until the employee actually clicks Accept - a row can exist (viewed) without this set.
    private LocalDateTime acceptedAt;

    // Typed full-name signature (e.g. "Jane Doe") - not an image/canvas signature, kept simple
    // per the acceptance flow's requirements.
    private String digitalSignatureText;

    private String ipAddress;
}
