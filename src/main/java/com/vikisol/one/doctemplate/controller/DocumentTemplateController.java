package com.vikisol.one.doctemplate.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.doctemplate.dto.DocumentTemplateRequest;
import com.vikisol.one.doctemplate.dto.DocumentTemplateResponse;
import com.vikisol.one.doctemplate.service.DocumentTemplateService;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// "Document Studio" - CEO/Super Admin/HR Admin only. Manages the versioned templates that
// DocumentGenerationService renders into PDFs for every HR document type.
@RestController
@RequestMapping("/document-templates")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
public class DocumentTemplateController {

    private final DocumentTemplateService templateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentTemplateResponse>>> listByType(@RequestParam Document.DocumentType type) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Templates retrieved", templateService.listByType(type)));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<DocumentTemplateResponse>> getActive(@RequestParam Document.DocumentType type) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Active template retrieved", templateService.getActive(type)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentTemplateResponse>> createVersion(
            @RequestBody DocumentTemplateRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Template version created", templateService.createVersion(request, principal.getUsername())));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<DocumentTemplateResponse>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Template activated", templateService.activate(id)));
    }
}
