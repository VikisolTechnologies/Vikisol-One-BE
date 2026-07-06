package com.vikisol.one.policy.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// A company policy document (leave policy, WFH policy, code of conduct, NDA, etc.) that employees
// may be required to read and digitally acknowledge. Brand-new table, so NOT NULL columns are
// safe here (no pre-existing rows to migrate against, unlike DocumentTemplate/other older tables).
//
// `content` is stored as a plain HTML string (same convention as DocumentTemplate.bodyHtml) rather
// than a separate rich-text block model - policies are simple, mostly-static documents, so the
// added complexity of a block schema (as used for generated PDF documents) isn't justified here.
// The frontend renders it via a sanitized HTML render.
@Entity
@Table(name = "company_policies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CompanyPolicy extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyCategory category;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String version;

    private java.time.LocalDate effectiveDate;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean requiresAcknowledgement = true;

    // Email of the CEO/HR Manager/Admin who created the policy - mirrors DocumentTemplate's
    // createdByEmail convention (kept as a denormalized string rather than a User/Employee FK,
    // since it's display-only and shouldn't break if the author's account is later deleted).
    private String createdByEmail;

    public enum PolicyCategory {
        LEAVE, ATTENDANCE, HYBRID, WFH, SECURITY, NDA, CODE_OF_CONDUCT, IT, POSH, OTHER
    }
}
