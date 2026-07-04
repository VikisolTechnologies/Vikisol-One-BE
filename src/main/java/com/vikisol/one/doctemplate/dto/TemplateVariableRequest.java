package com.vikisol.one.doctemplate.dto;

import com.vikisol.one.document.entity.Document;

public record TemplateVariableRequest(
        String key,
        String label,
        String description,
        Document.DocumentType documentType // null = global
) {
}
