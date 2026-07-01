package com.vikisol.one.attendance.dto;

public record CheckInRequest(
        String source,
        String remarks
) {
    public CheckInRequest {
        if (source == null || source.isBlank()) {
            source = "WEB";
        }
    }
}
