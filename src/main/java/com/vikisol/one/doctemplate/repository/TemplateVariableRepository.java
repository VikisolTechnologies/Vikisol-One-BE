package com.vikisol.one.doctemplate.repository;

import com.vikisol.one.doctemplate.entity.TemplateVariable;
import com.vikisol.one.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TemplateVariableRepository extends JpaRepository<TemplateVariable, UUID> {
    List<TemplateVariable> findByDocumentTypeIsNullOrDocumentType(Document.DocumentType documentType);
    boolean existsByKey(String key);
}
