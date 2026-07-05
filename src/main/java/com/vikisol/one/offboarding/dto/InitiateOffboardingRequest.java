package com.vikisol.one.offboarding.dto;

import com.vikisol.one.offboarding.entity.OffboardingCase;

import java.time.LocalDate;

public record InitiateOffboardingRequest(
        OffboardingCase.Type type,
        LocalDate lastWorkingDate,
        String reason,
        boolean bgvRequired
) {}
