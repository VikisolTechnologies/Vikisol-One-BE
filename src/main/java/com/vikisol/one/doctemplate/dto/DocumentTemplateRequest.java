package com.vikisol.one.doctemplate.dto;

import com.vikisol.one.document.entity.Document;

public record DocumentTemplateRequest(
        Document.DocumentType documentType,
        String name,
        String bodyHtml
) {
}
