package com.vikisol.one.offboarding.dto;

import com.vikisol.one.offboarding.entity.OffboardingCase;

public record AdvanceStageRequest(
        OffboardingCase.Stage stage,
        String comments
) {}
