package com.vikisol.one.analytics.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

// Single aggregate DTO backing GET /analytics/executive (CEO/HR-facing analytics dashboard).
// Each nested record is a real query result - no placeholders/mocked numbers. Where a metric could
// not be computed from data that actually exists in this system, the field/record is simply absent
// (see ReportService.getPayrollReport()'s pattern of NOT being reused here - we compute real payroll
// numbers directly from Payslip rows instead of returning zeros).
public record ExecutiveAnalyticsResponse(
        RecruitmentAnalytics recruitment,
        WorkforceAnalytics workforce,
        PayrollAnalytics payroll,
        LeaveAnalytics leave,
        BgvAnalytics bgv,
        AssetAnalytics assets
) {
    public record RecruitmentAnalytics(
            Map<String, Long> hiringFunnel,               // Candidate.Status -> count
            double candidateConversionRate,                // % of candidates that reached JOINED
            double interviewConversionRate,                 // interviews scheduled -> offers made (OFFER_MADE/OFFER_ACCEPTED/JOINED candidates with >=1 interview)
            double offerAcceptanceRate,                     // OFFER_ACCEPTED+JOINED / (OFFER_MADE+OFFER_ACCEPTED+OFFER_DECLINED+JOINED)
            Double avgTimeToHireDays,                       // avg(createdAt -> offeredDateOfJoining) for JOINED candidates, null if no data
            List<RecruiterPerformance> recruiterPerformance,
            List<HiringManagerPerformance> hiringManagerPerformance
    ) {}

    public record RecruiterPerformance(String recruiterName, long candidatesHandled, long hires) {}

    public record HiringManagerPerformance(String hiringManagerName, long candidatesHandled, long hires) {}

    public record WorkforceAnalytics(
            long totalEmployees,
            long activeCount,
            long probationCount,
            long noticePeriodCount,
            long offboardingCount,
            Map<String, Long> lifecycleStatusDistribution,
            Double attritionRatePercent,                    // exited+alumni in last 12 months / avg headcount, null if insufficient data
            Map<String, Long> headcountGrowthByMonth,        // "yyyy-MM" -> cumulative active headcount joined by that month
            Map<String, Long> departmentDistribution,
            Map<String, Long> locationDistribution           // Employee.city used as location
    ) {}

    public record PayrollAnalytics(
            int month,
            int year,
            BigDecimal monthlyPayrollTotal,
            Double payrollGrowthPercent,                     // vs previous month, null if no previous-month data
            Map<String, BigDecimal> costPerDepartment,
            BigDecimal highestSalary,
            BigDecimal averageSalary,
            Map<String, Long> salaryDistributionBuckets
    ) {}

    public record LeaveAnalytics(
            Map<String, Long> leaveRequestsByMonth,          // "yyyy-MM" -> count, last 6 months
            Map<String, Double> leaveDaysByType,             // LeaveType.name -> total days taken (APPROVED)
            double totalRemainingBalance,
            double totalUsedBalance
    ) {}

    public record BgvAnalytics(
            long pending,
            long cleared,
            long failed,
            Double avgCompletionDays                          // avg(reviewedAt - createdAt) for checks with reviewedAt set, null if none
    ) {}

    public record AssetAnalytics(
            long allocated,
            long available,
            long lost,
            long underMaintenance,
            long retired,
            Map<String, Long> byCategory
    ) {}
}
