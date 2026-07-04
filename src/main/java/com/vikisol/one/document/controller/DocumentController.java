package com.vikisol.one.document.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.document.dto.DocumentResponse;
import com.vikisol.one.document.dto.DocumentUploadRequest;
import com.vikisol.one.document.service.DocumentService;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
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

    // Proxies the stored file back with a real Content-Disposition header carrying a
    // human-readable filename - see DocumentService.downloadDocument for why this isn't just a
    // redirect to Cloudinary with a transformation flag (that path is blocked by a Cloudinary
    // account setting we don't control).
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable UUID id) {
        DocumentService.DownloadedFile file = documentService.downloadDocument(id);
        String encodedName = java.net.URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"; filename*=UTF-8''" + encodedName)
                .body(file.bytes());
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

    // Was reachable by any authenticated role (including a plain EMPLOYEE) with no restriction at
    // all - anyone could delete anyone else's stored documents, including verified/legal ones.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'CEO', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Document deleted successfully", null));
    }
}
