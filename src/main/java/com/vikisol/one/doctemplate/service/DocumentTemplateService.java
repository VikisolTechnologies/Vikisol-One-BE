package com.vikisol.one.doctemplate.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.doctemplate.dto.DocumentTemplateRequest;
import com.vikisol.one.doctemplate.dto.DocumentTemplateResponse;
import com.vikisol.one.doctemplate.entity.DocumentTemplate;
import com.vikisol.one.doctemplate.repository.DocumentTemplateRepository;
import com.vikisol.one.document.entity.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

// "Document Studio" backend: lets CEO/HR Admin manage templates per document type without any
// code change - creating a new template for a brand-new document type just means adding an enum
// value (Document.DocumentType) and calling createVersion(); no PDF-building Java code needed.
@Service
@RequiredArgsConstructor
public class DocumentTemplateService {

    private final DocumentTemplateRepository templateRepository;
    private final AuditService auditService;

    public List<DocumentTemplateResponse> listByType(Document.DocumentType type) {
        return templateRepository.findByDocumentTypeOrderByVersionDesc(type).stream()
                .map(this::toResponse).toList();
    }

    public DocumentTemplateResponse getActive(Document.DocumentType type) {
        DocumentTemplate template = templateRepository.findFirstByDocumentTypeAndIsActiveTrueOrderByVersionDesc(type)
                .orElseThrow(() -> new RuntimeException("No active template configured for " + type + " yet - create one in Document Studio"));
        return toResponse(template);
    }

    // Creates a new version for the document type and makes it the active one (previous
    // versions stay in history, selectable again via activate()).
    public DocumentTemplateResponse createVersion(DocumentTemplateRequest request, String createdByEmail) {
        List<DocumentTemplate> existing = templateRepository.findByDocumentType(request.documentType());
        int nextVersion = existing.stream().mapToInt(DocumentTemplate::getVersion).max().orElse(0) + 1;
        existing.forEach(t -> t.setActive(false));
        templateRepository.saveAll(existing);

        DocumentTemplate template = DocumentTemplate.builder()
                .documentType(request.documentType())
                .name(request.name())
                .version(nextVersion)
                .isActive(true)
                .bodyHtml(request.bodyHtml())
                .createdByEmail(createdByEmail)
                .build();
        template = templateRepository.save(template);
        auditService.record("Document Template Created", request.documentType().name(), request.name() + " v" + nextVersion);
        return toResponse(template);
    }

    public DocumentTemplateResponse activate(UUID id) {
        DocumentTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        List<DocumentTemplate> siblings = templateRepository.findByDocumentType(template.getDocumentType());
        siblings.forEach(t -> t.setActive(t.getId().equals(id)));
        templateRepository.saveAll(siblings);
        auditService.record("Document Template Activated", template.getDocumentType().name(), template.getName() + " v" + template.getVersion());
        return toResponse(template);
    }

    private DocumentTemplateResponse toResponse(DocumentTemplate t) {
        return new DocumentTemplateResponse(
                t.getId(), t.getDocumentType(), t.getName(), t.getVersion(),
                t.isActive(), t.getBodyHtml(), t.getCreatedByEmail(), t.getCreatedAt()
        );
    }
}
