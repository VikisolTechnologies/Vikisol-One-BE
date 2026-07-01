package com.vikisol.one.common.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @PostMapping("/files/upload/{subDirectory}")
    public ResponseEntity<ApiResponse<String>> uploadFile(@PathVariable String subDirectory,
                                                           @RequestParam("file") MultipartFile file) {
        String fileUrl = fileStorageService.storeFile(file, subDirectory);
        return ResponseEntity.ok(new ApiResponse<>(true, "File uploaded", fileUrl));
    }

    @GetMapping("/files/{subDirectory}/{fileName}")
    public ResponseEntity<byte[]> getFile(@PathVariable String subDirectory,
                                           @PathVariable String fileName) {
        byte[] data = fileStorageService.loadFile(subDirectory, fileName);
        String contentType = "application/octet-stream";
        if (fileName.endsWith(".pdf")) contentType = "application/pdf";
        else if (fileName.endsWith(".png")) contentType = "image/png";
        else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) contentType = "image/jpeg";
        else if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) contentType = "application/msword";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(data);
    }
}
