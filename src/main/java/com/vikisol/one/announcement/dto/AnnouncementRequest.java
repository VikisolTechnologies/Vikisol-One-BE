package com.vikisol.one.announcement.dto;

import com.vikisol.one.announcement.entity.Announcement;
import jakarta.validation.constraints.NotBlank;

public record AnnouncementRequest(
        @NotBlank String title,
        @NotBlank String message,
        Announcement.Priority priority
) {
}
