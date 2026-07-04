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
// code change - creating a template for a brand-new document type just means adding an enum
// value (Document.DocumentType), then calling createDraft() + publish(); no PDF-building Java
// code is needed for that new type.
@Service
@RequiredArgsConstructor
public class DocumentTemplateService {

    private final DocumentTemplateRepository templateRepository;
    private final AuditService auditService;

    public List<DocumentTemplateResponse> listByType(Document.DocumentType type) {
        return templateRepository.findByDocumentTypeOrderByNameAscVersionDesc(type).stream()
                .map(this::toResponse).toList();
    }

    public List<DocumentTemplateResponse> listVersions(String templateGroupId) {
        return templateRepository.findByTemplateGroupIdOrderByVersionDesc(templateGroupId).stream()
                .map(this::toResponse).toList();
    }

    public DocumentTemplateResponse getById(UUID id) {
        return toResponse(templateRepository.findById(id).orElseThrow(() -> new RuntimeException("Template not found")));
    }

    // Creates a brand-new named template (a new templateGroupId) at version 1, in DRAFT status -
    // it must be explicitly published before DocumentGenerationService will use it.
    public DocumentTemplateResponse createDraft(DocumentTemplateRequest request, String createdByEmail) {
        DocumentTemplate template = DocumentTemplate.builder()
                .documentType(request.documentType())
                .templateGroupId(UUID.randomUUID().toString())
                .name(request.name())
                .version(1)
                .status(DocumentTemplate.TemplateStatus.DRAFT)
                .bodyHtml(request.bodyHtml())
                .contentBlocksJson(request.contentBlocksJson())
                .createdByEmail(createdByEmail)
                .build();
        template = templateRepository.save(template);
        auditService.record("Document Template Created", request.documentType().name(), request.name() + " (draft)");
        return toResponse(template);
    }

    // Creates a new DRAFT version within an existing template group (e.g. editing "Corporate
    // Offer Letter" without touching the currently-published version until the new one is
    // explicitly published).
    public DocumentTemplateResponse createNewVersion(String templateGroupId, DocumentTemplateRequest request, String createdByEmail) {
        List<DocumentTemplate> existing = templateRepository.findByTemplateGroupIdOrderByVersionDesc(templateGroupId);
        if (existing.isEmpty()) throw new RuntimeException("Template group not found");
        int nextVersion = existing.get(0).getVersion() + 1;

        DocumentTemplate template = DocumentTemplate.builder()
                .documentType(request.documentType())
                .templateGroupId(templateGroupId)
                .name(request.name())
                .version(nextVersion)
                .status(DocumentTemplate.TemplateStatus.DRAFT)
                .bodyHtml(request.bodyHtml())
                .contentBlocksJson(request.contentBlocksJson())
                .createdByEmail(createdByEmail)
                .build();
        template = templateRepository.save(template);
        auditService.record("Document Template Version Created", request.documentType().name(), request.name() + " v" + nextVersion);
        return toResponse(template);
    }

    // Publishing a version archives every other PUBLISHED version in the same template group
    // (only one PUBLISHED version per group at a time), but leaves DRAFT/ARCHIVED siblings alone.
    public DocumentTemplateResponse publish(UUID id) {
        DocumentTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        List<DocumentTemplate> siblings = templateRepository.findByTemplateGroupIdOrderByVersionDesc(template.getTemplateGroupId());
        siblings.forEach(t -> {
            if (t.getStatus() == DocumentTemplate.TemplateStatus.PUBLISHED && !t.getId().equals(id)) {
                t.setStatus(DocumentTemplate.TemplateStatus.ARCHIVED);
            }
        });
        template.setStatus(DocumentTemplate.TemplateStatus.PUBLISHED);
        templateRepository.saveAll(siblings);
        templateRepository.save(template);
        auditService.record("Document Template Published", template.getDocumentType().name(), template.getName() + " v" + template.getVersion());
        return toResponse(template);
    }

    // Rolling back is just re-publishing an older (now ARCHIVED) version - publish() already
    // archives whatever is currently live, so this is the same operation from the caller's side.
    public DocumentTemplateResponse rollbackTo(UUID id) {
        return publish(id);
    }

    public DocumentTemplateResponse archive(UUID id) {
        DocumentTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        template.setStatus(DocumentTemplate.TemplateStatus.ARCHIVED);
        template = templateRepository.save(template);
        auditService.record("Document Template Archived", template.getDocumentType().name(), template.getName() + " v" + template.getVersion());
        return toResponse(template);
    }

    // Clones the given version into a brand-new template group, as a DRAFT - lets an admin
    // start "Intern Offer Letter" from a copy of "Corporate Offer Letter" instead of from scratch.
    public DocumentTemplateResponse duplicate(UUID id, String newName, String createdByEmail) {
        DocumentTemplate source = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Template not found"));
        DocumentTemplate copy = DocumentTemplate.builder()
                .documentType(source.getDocumentType())
                .templateGroupId(UUID.randomUUID().toString())
                .name(newName != null && !newName.isBlank() ? newName : source.getName() + " (Copy)")
                .version(1)
                .status(DocumentTemplate.TemplateStatus.DRAFT)
                .bodyHtml(source.getBodyHtml())
                .contentBlocksJson(source.getContentBlocksJson())
                .createdByEmail(createdByEmail)
                .build();
        copy = templateRepository.save(copy);
        auditService.record("Document Template Duplicated", source.getDocumentType().name(), source.getName() + " -> " + copy.getName());
        return toResponse(copy);
    }

    private DocumentTemplateResponse toResponse(DocumentTemplate t) {
        return new DocumentTemplateResponse(
                t.getId(), t.getTemplateGroupId(), t.getDocumentType(), t.getName(), t.getVersion(),
                t.getStatus(), t.getBodyHtml(), t.getContentBlocksJson(), t.getCreatedByEmail(), t.getCreatedAt()
        );
    }
}
