package com.vikisol.one.document.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.document.dto.DocumentResponse;
import com.vikisol.one.document.dto.DocumentUploadRequest;
import com.vikisol.one.document.service.DocumentService;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @Valid @RequestBody DocumentUploadRequest request) {
        DocumentResponse document = documentService.uploadDocument(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Document uploaded successfully", document));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getEmployeeDocuments(
            @PathVariable UUID employeeId) {
        List<DocumentResponse> documents = documentService.getEmployeeDocuments(employeeId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Documents retrieved successfully", documents));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getMyDocuments(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<DocumentResponse> documents = documentService.getMyDocuments(principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Documents retrieved successfully", documents));
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<DocumentResponse>> verifyDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        DocumentResponse document = documentService.verifyDocument(id, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Document verified successfully", document));
    }

    @GetMapping("/unverified")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'CEO', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getUnverifiedDocuments() {
        List<DocumentResponse> documents = documentService.getUnverifiedDocuments();
        return ResponseEntity.ok(new ApiResponse<>(true, "Unverified documents retrieved successfully", documents));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Document deleted successfully", null));
    }
}
