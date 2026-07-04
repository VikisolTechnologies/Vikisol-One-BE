package com.vikisol.one.doctemplate.repository;

import com.vikisol.one.doctemplate.entity.DocumentTemplate;
import com.vikisol.one.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {
    // The template used for generation when the caller doesn't ask for a specific templateId -
    // the most recent PUBLISHED version of the first/default template for that document type.
    Optional<DocumentTemplate> findFirstByDocumentTypeAndStatusOrderByVersionDesc(
            Document.DocumentType documentType, DocumentTemplate.TemplateStatus status);

    Optional<DocumentTemplate> findFirstByTemplateGroupIdAndStatusOrderByVersionDesc(
            String templateGroupId, DocumentTemplate.TemplateStatus status);

    List<DocumentTemplate> findByDocumentTypeOrderByNameAscVersionDesc(Document.DocumentType documentType);

    List<DocumentTemplate> findByTemplateGroupIdOrderByVersionDesc(String templateGroupId);

    boolean existsByDocumentType(Document.DocumentType documentType);
}
