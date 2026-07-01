package com.vikisol.one.resource.dto;

import com.vikisol.one.resource.entity.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ResourceRequest(
        @NotBlank String title,
        String description,
        @NotNull Resource.ResourceCategory category,
        String fileUrl,
        String externalLink,
        boolean isPublic
) {}
