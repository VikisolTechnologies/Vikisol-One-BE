package com.vikisol.one.doctemplate.dto;

import com.vikisol.one.doctemplate.entity.DocumentTemplate;
import com.vikisol.one.document.entity.Document;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentTemplateResponse(
        UUID id,
        String templateGroupId,
        Document.DocumentType documentType,
        String name,
        int version,
        DocumentTemplate.TemplateStatus status,
        String bodyHtml,
        String contentBlocksJson,
        String createdByEmail,
        LocalDateTime createdAt
) {
}
