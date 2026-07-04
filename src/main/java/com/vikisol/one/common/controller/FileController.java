package com.vikisol.one.common.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.service.FileModule;
import com.vikisol.one.common.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    // Returns a real, absolute Cloudinary URL - files are served directly from Cloudinary's CDN,
    // not proxied through this server, so there's no matching GET /files/** endpoint anymore.
    //
    // Only handles employee document uploads for now (the one real caller today - see
    // src/api/documents.js). Not a generic "upload to any module" endpoint on purpose: opening
    // that up would need per-module authorization this single endpoint doesn't have.
    @PostMapping("/files/upload")
    public ResponseEntity<ApiResponse<String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam String entityId,
            @RequestParam(defaultValue = "documents") String documentType) {
        String fileUrl = fileStorageService.storeFile(file, FileModule.EMPLOYEE, entityId, documentType);
        return ResponseEntity.ok(new ApiResponse<>(true, "File uploaded", fileUrl));
    }

    // Logos, signatures, letterhead, seal, etc. for the Company Branding module - stored under
    // FileModule.COMPANY rather than EMPLOYEE. assetType becomes the Cloudinary subfolder (e.g.
    // "logo", "ceo-signature", "hr-signature", "company-seal") so each asset type keeps its own
    // version history in the media library rather than overwriting a single fixed path.
    @PostMapping("/files/upload-branding-asset")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> uploadBrandingAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam String assetType) {
        String fileUrl = fileStorageService.storeFile(file, FileModule.COMPANY, "branding", assetType);
        return ResponseEntity.ok(new ApiResponse<>(true, "Asset uploaded", fileUrl));
    }
}
