package com.vikisol.one.doctemplate.dto;

import com.vikisol.one.document.entity.Document;

public record DocumentTemplateRequest(
        Document.DocumentType documentType,
        String name,
        // Structured block model (preferred) - see BlockRenderer for schema. If null/blank,
        // bodyHtml is used as a legacy raw-HTML fallback instead.
        String contentBlocksJson,
        String bodyHtml
) {
}
