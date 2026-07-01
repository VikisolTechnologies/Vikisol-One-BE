package com.vikisol.one.designation.dto;

public record DesignationRequest(
        String title,
        int level,
        String description
) {
}
