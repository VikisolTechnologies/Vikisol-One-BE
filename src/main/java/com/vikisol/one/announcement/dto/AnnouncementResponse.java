package com.vikisol.one.announcement.dto;

import com.vikisol.one.announcement.entity.Announcement;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnnouncementResponse(
        UUID id,
        String title,
        String message,
        Announcement.Priority priority,
        String postedByName,
        LocalDateTime createdAt
) {
}
