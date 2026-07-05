package com.vikisol.one.employee.dto;

import com.vikisol.one.employee.entity.BackgroundCheck;

public record BackgroundCheckUpdateRequest(
        BackgroundCheck.Status status,
        String remarks
) {
}
