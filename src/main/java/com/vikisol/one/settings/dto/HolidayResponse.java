package com.vikisol.one.settings.dto;

import com.vikisol.one.settings.entity.Holiday;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record HolidayResponse(
        UUID id,
        String name,
        LocalDate date,
        Holiday.HolidayType type,
        boolean isOptional,
        int year,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
