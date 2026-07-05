package com.vikisol.one.offboarding.dto;

import java.util.Map;

public record OffboardingDashboardStats(
        long totalActive,
        long completedThisMonth,
        Map<String, Long> byStage
) {}
