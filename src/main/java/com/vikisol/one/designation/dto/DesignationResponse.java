package com.vikisol.one.designation.dto;

import java.util.UUID;

public record DesignationResponse(
        UUID id,
        String title,
        int level,
        String description,
        boolean isActive
) {
}
