package com.vikisol.one.doctemplate.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.document.entity.Document;
import jakarta.persistence.*;
import lombok.*;

// A named, versioned HTML template for a document type (e.g. "Offer Letter - Corporate"). Body
// contains {{PlaceholderName}} tokens that DocumentGenerationService fills in at render time.
// Only one version per documentType is "active" at a time - that's the one used for generation
// unless the caller explicitly asks for a specific templateId (multi-template support: e.g.
// "Offer Letter Version 2" and "Intern Offer Letter" can both exist and be selected between).
@Entity
@Table(name = "document_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DocumentTemplate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Document.DocumentType documentType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int version;

    @Builder.Default
    @Column(nullable = false)
    private boolean isActive = true;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String bodyHtml;

    private String createdByEmail;
}
