package com.vikisol.one.document.dto;

import com.vikisol.one.document.entity.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID employeeId,
        String employeeName,
        String title,
        Document.DocumentType type,
        String fileUrl,
        String fileName,
        long fileSize,
        String mimeType,
        String description,
        boolean isVerified,
        UUID verifiedById,
        String verifiedByName,
        LocalDateTime verifiedDate,
        LocalDate expiryDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
