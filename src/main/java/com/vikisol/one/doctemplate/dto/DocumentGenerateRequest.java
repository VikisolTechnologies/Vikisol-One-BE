package com.vikisol.one.doctemplate.dto;

import com.vikisol.one.document.entity.Document;

import java.util.Map;
import java.util.UUID;

// The generic "generate any document type" request. fields carries whatever is type-specific
// (e.g. {"Reason": "..."} for Warning Letter, {"NewDesignation": "..."} for Promotion Letter) -
// EmployeeName/Designation/Department/etc are resolved automatically from employeeId and don't
// need to be passed in. templateId is optional - omit it to use the currently published default.
public record DocumentGenerateRequest(
        Document.DocumentType documentType,
        UUID employeeId,
        UUID templateId,
        Map<String, String> fields
) {
}
