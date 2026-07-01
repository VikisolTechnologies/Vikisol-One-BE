package com.vikisol.one.document.dto;

import com.vikisol.one.document.entity.Document;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DocumentUploadRequest(
        @NotNull UUID employeeId,
        @NotBlank String title,
        @NotNull Document.DocumentType type,
        @NotBlank String fileUrl,
        String fileName,
        long fileSize,
        String mimeType,
        String description
) {}
