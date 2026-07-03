package com.vikisol.one.common.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    // Returns a real, absolute Cloudinary URL - files are served directly from Cloudinary's CDN,
    // not proxied through this server, so there's no matching GET /files/** endpoint anymore.
    @PostMapping("/files/upload/{subDirectory}")
    public ResponseEntity<ApiResponse<String>> uploadFile(@PathVariable String subDirectory,
                                                           @RequestParam("file") MultipartFile file) {
        String fileUrl = fileStorageService.storeFile(file, subDirectory);
        return ResponseEntity.ok(new ApiResponse<>(true, "File uploaded", fileUrl));
    }
}
