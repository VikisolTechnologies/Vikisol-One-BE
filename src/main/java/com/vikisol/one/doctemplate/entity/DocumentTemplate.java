package com.vikisol.one.doctemplate.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.document.entity.Document;
import jakarta.persistence.*;
import lombok.*;

// A named, versioned template for a document type (e.g. "Offer Letter - Corporate"). Content is
// stored as structured blocks (contentBlocksJson - see BlockRenderer for the schema: heading,
// paragraph, table, list, signatureBlock, spacer), not one hardcoded HTML string - this is what
// lets a future drag-and-drop designer, DOCX export, or version diff work against the same data
// without a redesign. bodyHtml is a legacy fallback only: the handful of templates seeded before
// the block model existed still render from it if contentBlocksJson is empty.
//
// Multiple templates can exist per documentType (e.g. "Corporate Offer Letter" + "Intern Offer
// Letter" as separate rows, both PUBLISHED, selectable by templateId at generation time), and
// each one is independently versioned: creating a new version archives the previously-published
// version of THAT SAME named template rather than every template of that documentType.
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

    // Groups versions of "the same" template together (e.g. all versions of "Corporate Offer
    // Letter" share a templateGroupId even as `id` changes per version row).
    //
    // Not DB-NOT-NULL: ddl-auto=update can't backfill a NOT NULL column against pre-existing
    // rows (the ALTER TABLE fails and Hibernate silently skips adding the column, which is worse
    // than a nullable column - it previously caused a real "column does not exist" 500 in prod).
    // DataSeeder.migrateLegacyDocumentTemplates() backfills old rows on startup instead.
    private String templateGroupId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TemplateStatus status = TemplateStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String bodyHtml;

    // JSON array of content blocks - see BlockRenderer for schema/types.
    @Column(columnDefinition = "TEXT")
    private String contentBlocksJson;

    private String createdByEmail;

    public enum TemplateStatus {
        DRAFT, PUBLISHED, ARCHIVED
    }
}
