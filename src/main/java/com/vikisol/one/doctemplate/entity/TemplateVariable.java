package com.vikisol.one.doctemplate.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.document.entity.Document;
import jakarta.persistence.*;
import lombok.*;

// The registry of {{Placeholder}} tokens available to template authors. This is what makes
// "adding a new placeholder shouldn't require code changes" literally true: an admin adds a row
// here (documentType null = usable in every document type, e.g. {{EmployeeName}}; or scoped to
// one type, e.g. {{NewDesignation}} only for Promotion Letter), and it immediately shows up as
// an insertable token in Document Studio - no Java change needed. The actual VALUE still comes
// from whatever the caller supplies at generation time (see DocumentController); this table only
// tracks which tokens *exist* and what they mean, for template-authoring UI purposes.
@Entity
@Table(name = "template_variables")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TemplateVariable extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String key; // e.g. "EmployeeName" - used as {{EmployeeName}}

    @Column(nullable = false)
    private String label; // e.g. "Employee Full Name"

    private String description;

    // Null = global (available to every document type). Non-null = only meaningful for that type.
    @Enumerated(EnumType.STRING)
    private Document.DocumentType documentType;
}
