package com.vikisol.one.department.dto;

import java.util.UUID;

public record DepartmentRequest(
        String name,
        String code,
        String description,
        UUID managerId
) {
}
