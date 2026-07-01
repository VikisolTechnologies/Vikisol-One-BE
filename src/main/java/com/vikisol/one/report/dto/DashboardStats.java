package com.vikisol.one.report.dto;

import java.util.Map;

public record DashboardStats(
        long totalEmployees,
        long activeEmployees,
        long newJoineesThisMonth,
        long onNoticeCount,
        Map<String, Long> departmentWiseCount,
        Map<String, Long> genderDistribution,
        Map<String, Long> employmentTypeDistribution
) {}
