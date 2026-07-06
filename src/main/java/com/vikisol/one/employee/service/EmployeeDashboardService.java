package com.vikisol.one.employee.service;

import com.vikisol.one.announcement.dto.AnnouncementResponse;
import com.vikisol.one.announcement.service.AnnouncementService;
import com.vikisol.one.asset.dto.AssetAssignmentResponse;
import com.vikisol.one.asset.service.AssetService;
import com.vikisol.one.attendance.dto.AttendanceResponse;
import com.vikisol.one.attendance.dto.MonthlyAttendanceSummary;
import com.vikisol.one.attendance.service.AttendanceService;
import com.vikisol.one.document.dto.DocumentResponse;
import com.vikisol.one.document.service.DocumentService;
import com.vikisol.one.employee.dto.EmployeeDashboardSummaryResponse;
import com.vikisol.one.employee.dto.ProfileCompletionResponse;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.leave.dto.LeaveBalanceResponse;
import com.vikisol.one.leave.entity.LeaveRequest;
import com.vikisol.one.leave.repository.LeaveRequestRepository;
import com.vikisol.one.leave.service.LeaveService;
import com.vikisol.one.payroll.dto.PayslipResponse;
import com.vikisol.one.payroll.service.PayrollService;
import com.vikisol.one.performance.entity.PerformanceReview;
import com.vikisol.one.performance.repository.PerformanceReviewRepository;
import com.vikisol.one.policy.entity.CompanyPolicy;
import com.vikisol.one.policy.service.PolicyAcknowledgementService;
import com.vikisol.one.project.dto.ProjectMemberResponse;
import com.vikisol.one.project.entity.ProjectMember;
import com.vikisol.one.project.repository.ProjectMemberRepository;
import com.vikisol.one.recruitment.entity.Interview;
import com.vikisol.one.recruitment.repository.InterviewRepository;
import com.vikisol.one.settings.dto.HolidayResponse;
import com.vikisol.one.settings.service.SettingsService;
import com.vikisol.one.timesheet.entity.TimesheetEntry;
import com.vikisol.one.timesheet.repository.TimesheetEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Aggregates the employee-facing "Home" dashboard from every module that already has real,
// employee-scoped data - one round trip instead of ~15. Anything without a genuine backing data
// source (company-wide announcements when none exist, employee certifications, etc.) is left out
// of the response entirely rather than being backfilled with a fabricated value; see
// EmployeeDashboard.jsx / the delivery report for the authoritative list of what's real vs omitted.
@Service
@RequiredArgsConstructor
public class EmployeeDashboardService {

    private final EmployeeRepository employeeRepository;
    private final OnboardingService onboardingService;
    private final BackgroundCheckService backgroundCheckService;
    private final LeaveService leaveService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceService attendanceService;
    private final SettingsService settingsService;
    private final AssetService assetService;
    private final PayrollService payrollService;
    private final DocumentService documentService;
    private final AnnouncementService announcementService;
    private final PolicyAcknowledgementService policyAcknowledgementService;
    private final InterviewRepository interviewRepository;
    private final TimesheetEntryRepository timesheetEntryRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final PerformanceReviewRepository performanceReviewRepository;

    private static final int UPCOMING_WINDOW_DAYS = 30;

    @Transactional(readOnly = true)
    public EmployeeDashboardSummaryResponse getSummary(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        LocalDate today = LocalDate.now();
        int year = today.getYear();

        // Profile completion
        ProfileCompletionResponse completion = onboardingService.getProfileCompletion(employeeId);

        // BGV
        long bgvApproved = backgroundCheckService.getForEmployee(employeeId).stream()
                .filter(b -> b.status() == com.vikisol.one.employee.entity.BackgroundCheck.Status.APPROVED)
                .count();
        int bgvTotal = com.vikisol.one.employee.entity.BackgroundCheck.CheckType.values().length;

        // Leave
        List<LeaveBalanceResponse> leaveBalances = leaveService.getBalancesForEmployee(employeeId, year);
        long myPendingLeave = leaveRequestRepository.findByEmployeeIdAndStatus(employeeId, LeaveRequest.LeaveStatus.PENDING).size();
        long pendingApprovalsForMe = leaveService.countPendingApprovalsForEmployee(employeeId);

        // Attendance
        AttendanceResponse todayAttendance = attendanceService.getTodayAttendanceForEmployee(employeeId);
        MonthlyAttendanceSummary monthlySummary = attendanceService.getMonthlyAttendanceSummary(employeeId, today.getYear(), today.getMonthValue());

        // Holidays - next 5 upcoming (this year, falling back into next year near year-end)
        List<HolidayResponse> upcomingHolidays = settingsService.getHolidaysForYear(year).stream()
                .filter(h -> !h.date().isBefore(today))
                .sorted(Comparator.comparing(HolidayResponse::date))
                .limit(5)
                .toList();
        if (upcomingHolidays.size() < 5) {
            List<HolidayResponse> nextYear = settingsService.getHolidaysForYear(year + 1).stream()
                    .sorted(Comparator.comparing(HolidayResponse::date))
                    .limit(5 - upcomingHolidays.size())
                    .toList();
            upcomingHolidays = java.util.stream.Stream.concat(upcomingHolidays.stream(), nextYear.stream()).toList();
        }

        // Birthdays across the company in the next 30 days (month/day match, wrapping year-end)
        List<EmployeeDashboardSummaryResponse.UpcomingBirthday> upcomingBirthdays =
                employeeRepository.findByIsActiveTrue(org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                        .filter(e -> e.getDateOfBirth() != null)
                        .filter(e -> daysUntilNextBirthday(e.getDateOfBirth(), today) <= UPCOMING_WINDOW_DAYS)
                        .sorted(Comparator.comparingInt(e -> daysUntilNextBirthday(e.getDateOfBirth(), today)))
                        .map(e -> new EmployeeDashboardSummaryResponse.UpcomingBirthday(e.getId(), e.getFirstName() + " " + e.getLastName(), e.getDateOfBirth()))
                        .toList();

        // Assets
        List<AssetAssignmentResponse> myAssets = assetService.getEmployeeAssets(employeeId);

        // Payroll
        PayslipResponse lastPayslip = payrollService.getLatestPayslip(employeeId);

        // Documents
        List<DocumentResponse> recentDocuments = documentService.getRecentDocuments(employeeId, 5);

        // Announcements
        List<AnnouncementResponse> announcements = announcementService.getAll().stream().limit(5).toList();

        // Policies pending acknowledgement
        List<EmployeeDashboardSummaryResponse.PendingPolicy> pendingPolicies =
                policyAcknowledgementService.getPendingPolicies(employeeId).stream()
                        .map(p -> new EmployeeDashboardSummaryResponse.PendingPolicy(p.getId(), p.getTitle()))
                        .toList();

        // Upcoming interviews where this employee is the primary interviewer
        List<EmployeeDashboardSummaryResponse.UpcomingInterview> upcomingInterviews =
                interviewRepository.findByInterviewerIdAndScheduledDateGreaterThanEqual(employeeId, today).stream()
                        .filter(i -> i.getStatus() == Interview.Status.SCHEDULED || i.getStatus() == Interview.Status.RESCHEDULED)
                        .sorted(Comparator.comparing(Interview::getScheduledDate))
                        .map(i -> new EmployeeDashboardSummaryResponse.UpcomingInterview(
                                i.getId(),
                                i.getCandidate() != null ? i.getCandidate().getFirstName() + " " + i.getCandidate().getLastName() : null,
                                i.getTitle(), i.getScheduledDate(), i.getScheduledTime()))
                        .toList();

        // Timesheet - current week hours + draft (not yet submitted) entries
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<TimesheetEntry> weekEntries = timesheetEntryRepository.findByEmployeeIdAndDateBetween(employeeId, weekStart, weekEnd);
        double weekHours = weekEntries.stream().mapToDouble(t -> t.getHours() != null ? t.getHours() : 0).sum();
        long draftEntries = timesheetEntryRepository.findByEmployeeIdAndStatus(employeeId, TimesheetEntry.Status.DRAFT).size();

        // Projects
        List<ProjectMemberResponse> currentProjects = projectMemberRepository.findByEmployeeIdAndIsActiveTrue(employeeId).stream()
                .map(this::toProjectMemberResponse)
                .toList();

        // Performance - most recent review of any status
        EmployeeDashboardSummaryResponse.LatestPerformanceReview latestReview =
                performanceReviewRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                        .findFirst()
                        .map(r -> new EmployeeDashboardSummaryResponse.LatestPerformanceReview(
                                r.getReviewCycle().getName(), r.getStatus().name(), r.getOverallSelfRating(), r.getOverallManagerRating()))
                        .orElse(null);

        return new EmployeeDashboardSummaryResponse(
                employee.getFirstName() + " " + employee.getLastName(),
                employee.getDesignation() != null ? employee.getDesignation().getTitle() : null,
                completion.percent(), completion.missing(),
                (int) bgvApproved, bgvTotal,
                leaveBalances, myPendingLeave, pendingApprovalsForMe,
                todayAttendance, monthlySummary,
                upcomingHolidays, upcomingBirthdays,
                myAssets,
                lastPayslip,
                recentDocuments,
                announcements,
                pendingPolicies,
                upcomingInterviews,
                weekHours, draftEntries,
                currentProjects,
                latestReview
        );
    }

    // Days from `from` until the next occurrence of `dob`'s month/day, wrapping into next year.
    private int daysUntilNextBirthday(LocalDate dob, LocalDate from) {
        MonthDay birthdayThisYear = MonthDay.of(dob.getMonthValue(), dob.getDayOfMonth() == 29 && dob.getMonthValue() == 2 && !LocalDate.now().isLeapYear() ? 28 : dob.getDayOfMonth());
        LocalDate next = birthdayThisYear.atYear(from.getYear());
        if (next.isBefore(from)) {
            next = birthdayThisYear.atYear(from.getYear() + 1);
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(from, next);
    }

    private ProjectMemberResponse toProjectMemberResponse(ProjectMember m) {
        ProjectMemberResponse r = new ProjectMemberResponse();
        r.setId(m.getId());
        r.setProjectId(m.getProject().getId());
        r.setProjectName(m.getProject().getName());
        r.setEmployeeId(m.getEmployee().getId());
        r.setEmployeeName(m.getEmployee().getFirstName() + " " + m.getEmployee().getLastName());
        r.setRole(m.getRole());
        r.setAllocationPercentage(m.getAllocationPercentage());
        r.setStartDate(m.getStartDate());
        r.setEndDate(m.getEndDate());
        r.setActive(m.isActive());
        r.setCreatedAt(m.getCreatedAt());
        r.setUpdatedAt(m.getUpdatedAt());
        return r;
    }
}
