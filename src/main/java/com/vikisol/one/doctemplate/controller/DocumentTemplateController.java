package com.vikisol.one.doctemplate.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.doctemplate.dto.DocumentTemplateRequest;
import com.vikisol.one.doctemplate.dto.DocumentTemplateResponse;
import com.vikisol.one.doctemplate.service.DocumentTemplateService;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.config.DataSeeder;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// "Document Studio" - CEO/HR Manager/Admin only. Manages the versioned templates that
// DocumentGenerationService renders into PDFs for every HR document type.
@RestController
@RequestMapping("/document-templates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
public class DocumentTemplateController {

    private final DocumentTemplateService templateService;
    private final DataSeeder dataSeeder;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentTemplateResponse>>> listByType(@RequestParam Document.DocumentType type) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Templates retrieved", templateService.listByType(type)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentTemplateResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Template retrieved", templateService.getById(id)));
    }

    @GetMapping("/group/{templateGroupId}/versions")
    public ResponseEntity<ApiResponse<List<DocumentTemplateResponse>>> listVersions(@PathVariable String templateGroupId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Versions retrieved", templateService.listVersions(templateGroupId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentTemplateResponse>> createDraft(
            @RequestBody DocumentTemplateRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Template draft created", templateService.createDraft(request, principal.getUsername())));
    }

    @PostMapping("/group/{templateGroupId}/versions")
    public ResponseEntity<ApiResponse<DocumentTemplateResponse>> createNewVersion(
            @PathVariable String templateGroupId, @RequestBody DocumentTemplateRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "New draft version created", templateService.createNewVersion(templateGroupId, request, principal.getUsername())));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ApiResponse<DocumentTemplateResponse>> duplicate(
            @PathVariable UUID id, @RequestParam(required = false) String name, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Template duplicated", templateService.duplicate(id, name, principal.getUsername())));
    }

    @PutMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<DocumentTemplateResponse>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Template published", templateService.publish(id)));
    }

    // Rolling back is the same operation as publishing an older/archived version.
    @PutMapping("/{id}/rollback")
    public ResponseEntity<ApiResponse<DocumentTemplateResponse>> rollback(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Rolled back to this version", templateService.rollbackTo(id)));
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<DocumentTemplateResponse>> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Template archived", templateService.archive(id)));
    }

    // Deliberate, human-triggered creation of the default Corporate Offer Letter template in
    // production - templates are business content, so they're never auto-seeded on startup there
    // (see DataSeeder.run()'s profile guard). This still goes through createDraft()/publish(),
    // so it's the same audited, versioned path as anything authored directly in Document Studio.
    // No-op (existsByDocumentType guard) if the template already exists.
    @PostMapping("/seed-offer-letter")
    public ResponseEntity<ApiResponse<Void>> seedOfferLetter() {
        dataSeeder.seedOfferLetterTemplate();
        return ResponseEntity.ok(new ApiResponse<>(true, "Offer Letter template seeded (no-op if it already existed)", null));
    }

    // Same rationale as seed-offer-letter, but for the rest of the default template pack
    // (Experience/Relieving/Salary Certificate/Payslip/Appointment/Joining/Confirmation/
    // Promotion/Salary Revision/Resignation Acceptance/Termination/Warning/Internship/Contract/
    // Leave Approval-Rejection/Employment Verification, plus registered template variables) -
    // needed for a fresh production environment where these were never seeded, since they're now
    // also gated to non-prod auto-seeding. No-op per type if it already exists.
    @PostMapping("/seed-defaults")
    public ResponseEntity<ApiResponse<Void>> seedDefaults() {
        dataSeeder.seedDefaultDocumentTemplates();
        return ResponseEntity.ok(new ApiResponse<>(true, "Default document templates seeded (no-op for any type that already existed)", null));
    }
}
