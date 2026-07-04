package com.vikisol.one.doctemplate.dto;

import com.vikisol.one.document.entity.Document;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentTemplateResponse(
        UUID id,
        Document.DocumentType documentType,
        String name,
        int version,
        boolean isActive,
        String bodyHtml,
        String createdByEmail,
        LocalDateTime createdAt
) {
}
