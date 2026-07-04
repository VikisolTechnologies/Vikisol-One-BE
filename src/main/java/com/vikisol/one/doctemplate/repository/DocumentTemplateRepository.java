package com.vikisol.one.doctemplate.repository;

import com.vikisol.one.doctemplate.entity.DocumentTemplate;
import com.vikisol.one.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {
    Optional<DocumentTemplate> findFirstByDocumentTypeAndIsActiveTrueOrderByVersionDesc(Document.DocumentType documentType);
    List<DocumentTemplate> findByDocumentTypeOrderByVersionDesc(Document.DocumentType documentType);
    List<DocumentTemplate> findByDocumentType(Document.DocumentType documentType);
}
