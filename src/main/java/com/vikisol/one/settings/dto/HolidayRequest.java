package com.vikisol.one.settings.dto;

import com.vikisol.one.settings.entity.Holiday;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record HolidayRequest(
        @NotBlank String name,
        @NotNull LocalDate date,
        @NotNull Holiday.HolidayType type,
        boolean isOptional,
        String description
) {}
