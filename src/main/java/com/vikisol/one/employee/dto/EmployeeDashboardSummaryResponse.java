package com.vikisol.one.employee.dto;

import com.vikisol.one.announcement.dto.AnnouncementResponse;
import com.vikisol.one.asset.dto.AssetAssignmentResponse;
import com.vikisol.one.attendance.dto.AttendanceResponse;
import com.vikisol.one.attendance.dto.MonthlyAttendanceSummary;
import com.vikisol.one.document.dto.DocumentResponse;
import com.vikisol.one.leave.dto.LeaveBalanceResponse;
import com.vikisol.one.payroll.dto.PayslipResponse;
import com.vikisol.one.project.dto.ProjectMemberResponse;
import com.vikisol.one.settings.dto.HolidayResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// Single aggregate for the employee-facing "Home" dashboard - composes real data already owned
// by each existing module's service. Any field genuinely without a backing data source (company
// announcements when none exist, certifications, etc.) is simply omitted rather than faked here;
// see the frontend/report for the authoritative list of what was and wasn't sourced.
public record EmployeeDashboardSummaryResponse(
        String employeeName,
        String designation,

        // Onboarding / compliance
        int profileCompletionPercent,
        List<String> missingProfileSections,
        int bgvApprovedCount,
        int bgvTotalCount,

        // Leave
        List<LeaveBalanceResponse> leaveBalances,
        long myPendingLeaveRequests,
        long pendingApprovalsForMe,

        // Attendance
        AttendanceResponse todayAttendance,
        MonthlyAttendanceSummary monthlyAttendanceSummary,

        // Holidays / birthdays
        List<HolidayResponse> upcomingHolidays,
        List<UpcomingBirthday> upcomingBirthdays,

        // Assets
        List<AssetAssignmentResponse> myAssets,

        // Payroll
        PayslipResponse lastPayslip,

        // Documents
        List<DocumentResponse> recentDocuments,

        // Announcements
        List<AnnouncementResponse> announcements,

        // Policies
        List<PendingPolicy> pendingPolicyAcknowledgements,

        // Recruitment (only populated if this employee is an interviewer)
        List<UpcomingInterview> upcomingInterviews,

        // Timesheet
        double weekTimesheetHours,
        long draftTimesheetEntries,

        // Projects
        List<ProjectMemberResponse> currentProjects,

        // Performance
        LatestPerformanceReview latestPerformanceReview
) {
    public record UpcomingBirthday(UUID employeeId, String name, LocalDate dateOfBirth) {}

    public record PendingPolicy(UUID policyId, String title) {}

    public record UpcomingInterview(UUID id, String candidateName, String title, LocalDate scheduledDate, LocalTime scheduledTime) {}

    public record LatestPerformanceReview(String reviewCycleName, String status, Double overallSelfRating, Double overallManagerRating) {}
}
