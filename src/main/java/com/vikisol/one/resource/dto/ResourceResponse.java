package com.vikisol.one.resource.dto;

import com.vikisol.one.resource.entity.Resource;

import java.time.LocalDateTime;
import java.util.UUID;

public record ResourceResponse(
        UUID id,
        String title,
        String description,
        Resource.ResourceCategory category,
        String fileUrl,
        String externalLink,
        boolean isPublic,
        UUID uploadedById,
        String uploadedByName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
