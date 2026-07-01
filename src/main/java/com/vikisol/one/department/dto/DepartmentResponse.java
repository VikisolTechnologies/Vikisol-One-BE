package com.vikisol.one.department.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DepartmentResponse(
        UUID id,
        String name,
        String code,
        String description,
        String managerName,
        boolean isActive,
        LocalDateTime createdAt
) {
}
