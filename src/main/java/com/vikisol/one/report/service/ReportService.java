package com.vikisol.one.report.service;

import com.vikisol.one.attendance.entity.Attendance;
import com.vikisol.one.attendance.repository.AttendanceRepository;
import com.vikisol.one.common.service.PdfService;
import com.vikisol.one.document.repository.DocumentRepository;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.employee.service.BackgroundCheckService;
import com.vikisol.one.employee.service.OnboardingService;
import com.vikisol.one.report.dto.AttendanceReportResponse;
import com.vikisol.one.report.dto.DashboardStats;
import com.vikisol.one.report.dto.HeadcountReportResponse;
import com.vikisol.one.report.dto.PayrollReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final EmployeeRepository employeeRepository;
    private final OnboardingService onboardingService;
    private final BackgroundCheckService backgroundCheckService;
    private final DocumentRepository documentRepository;
    private final AttendanceRepository attendanceRepository;
    private final PdfService pdfService;

    public DashboardStats getDashboardStats() {
        List<Employee> allEmployees = employeeRepository.findAll();
        List<Employee> activeEmployees = allEmployees.stream()
                .filter(Employee::isActive)
                .collect(Collectors.toList());

        long totalEmployees = allEmployees.size();
        long activeCount = activeEmployees.size();
        long onNoticeCount = employeeRepository.countByEmploymentStatus(Employee.EmploymentStatus.ON_NOTICE);

        LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
        long newJoineesThisMonth = activeEmployees.stream()
                .filter(e -> e.getDateOfJoining() != null && !e.getDateOfJoining().isBefore(startOfMonth))
                .count();

        Map<String, Long> departmentWiseCount = activeEmployees.stream()
                .filter(e -> e.getDepartment() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getDepartment().getName(),
                        Collectors.counting()
                ));

        Map<String, Long> genderDistribution = activeEmployees.stream()
                .filter(e -> e.getGender() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getGender().name(),
                        Collectors.counting()
                ));

        Map<String, Long> employmentTypeDistribution = activeEmployees.stream()
                .filter(e -> e.getEmploymentType() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getEmploymentType().name(),
                        Collectors.counting()
                ));

        // Pending-X widgets are computed per active employee rather than a single aggregate SQL
        // query - consistent with the rest of this method (which already loads all employees into
        // memory and stream-processes), and at this app's scale (dozens-hundreds of employees) an
        // N+1-shaped loop here is not a real bottleneck.
        long pendingOnboardingCount = activeEmployees.stream()
                .filter(e -> onboardingService.getProfileCompletion(e.getId()).percent() < 100)
                .count();
        long pendingBgvCount = activeEmployees.stream()
                .filter(e -> !backgroundCheckService.isFullyApproved(e.getId()))
                .count();
        long pendingDocumentsCount = activeEmployees.stream()
                .filter(e -> documentRepository.findByEmployeeId(e.getId()).isEmpty())
                .count();

        return new DashboardStats(
                totalEmployees, activeCount, newJoineesThisMonth, onNoticeCount,
                departmentWiseCount, genderDistribution, employmentTypeDistribution,
                pendingOnboardingCount, pendingBgvCount, pendingDocumentsCount
        );
    }

    public List<AttendanceReportResponse> getAttendanceReport(int month, int year) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        List<Attendance> records = attendanceRepository.findByDateBetween(start, end);

        Map<UUID, List<Attendance>> byEmployee = records.stream()
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId()));

        return byEmployee.values().stream()
                .map(list -> {
                    Employee emp = list.get(0).getEmployee();
                    long present = list.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
                    long absent = list.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
                    long halfDays = list.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.HALF_DAY).count();
                    long leaveDays = list.stream().filter(a -> a.getStatus() == Attendance.AttendanceStatus.ON_LEAVE).count();
                    double avgWorkingHours = list.stream().mapToDouble(Attendance::getWorkingHours).average().orElse(0);
                    return new AttendanceReportResponse(
                            emp.getFirstName() + " " + emp.getLastName(), emp.getEmployeeId(),
                            present, absent, halfDays, leaveDays, avgWorkingHours, month, year);
                })
                .sorted(Comparator.comparing(AttendanceReportResponse::employeeName))
                .collect(Collectors.toList());
    }

    public byte[] renderAttendanceReportPdf(int month, int year) {
        List<AttendanceReportResponse> rows = getAttendanceReport(month, year);
        String monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        StringBuilder rowsHtml = new StringBuilder();
        if (rows.isEmpty()) {
            rowsHtml.append("<tr><td colspan=\"6\" style=\"text-align:center;color:#6b7280;padding:16px;\">No attendance data for this period</td></tr>");
        } else {
            for (AttendanceReportResponse r : rows) {
                rowsHtml.append("<tr>")
                        .append("<td>").append(escapeHtml(r.employeeName())).append(" (").append(escapeHtml(r.employeeId())).append(")</td>")
                        .append("<td>").append(r.presentDays()).append("</td>")
                        .append("<td>").append(r.absentDays()).append("</td>")
                        .append("<td>").append(r.halfDays()).append("</td>")
                        .append("<td>").append(r.leaveDays()).append("</td>")
                        .append("<td>").append(String.format(Locale.ENGLISH, "%.1f", r.avgWorkingHours())).append("</td>")
                        .append("</tr>");
            }
        }

        String xhtml = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><style>"
                + "body { font-family: Helvetica, Arial, sans-serif; color: #1f2937; }"
                + "h1 { font-size: 18px; margin-bottom: 4px; }"
                + "p.sub { color: #6b7280; font-size: 11px; margin-top: 0; margin-bottom: 16px; }"
                + "table { width: 100%; border-collapse: collapse; font-size: 11px; }"
                + "th { background: #f3f4f6; text-align: left; padding: 6px 8px; border-bottom: 1px solid #d1d5db; }"
                + "td { padding: 6px 8px; border-bottom: 1px solid #e5e7eb; }"
                + "</style></head><body>"
                + "<h1>Attendance Report</h1>"
                + "<p class=\"sub\">" + monthName + " " + year + "</p>"
                + "<table><thead><tr><th>Employee</th><th>Present</th><th>Absent</th><th>Half Days</th><th>Leave</th><th>Avg Hours</th></tr></thead>"
                + "<tbody>" + rowsHtml + "</tbody></table>"
                + "</body></html>";

        return pdfService.renderPdf(xhtml);
    }

    private String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public PayrollReportResponse getPayrollReport(int month, int year) {
        // TODO: Integrate with Payroll module when available
        return new PayrollReportResponse(
                month, year,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, Collections.emptyMap()
        );
    }

    public HeadcountReportResponse getHeadcountReport(LocalDate date) {
        List<Employee> allEmployees = employeeRepository.findAll();
        List<Employee> activeEmployees = allEmployees.stream()
                .filter(Employee::isActive)
                .collect(Collectors.toList());

        Map<String, Long> departmentWise = activeEmployees.stream()
                .filter(e -> e.getDepartment() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getDepartment().getName(),
                        Collectors.counting()
                ));

        Map<String, Long> designationWise = activeEmployees.stream()
                .filter(e -> e.getDesignation() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getDesignation().getTitle(),
                        Collectors.counting()
                ));

        LocalDate startOfMonth = date.withDayOfMonth(1);
        LocalDate endOfMonth = date.withDayOfMonth(date.lengthOfMonth());

        long newJoinees = activeEmployees.stream()
                .filter(e -> e.getDateOfJoining() != null
                        && !e.getDateOfJoining().isBefore(startOfMonth)
                        && !e.getDateOfJoining().isAfter(endOfMonth))
                .count();

        long exits = allEmployees.stream()
                .filter(e -> !e.isActive())
                .count();

        return new HeadcountReportResponse(
                date, activeEmployees.size(), departmentWise, designationWise, newJoinees, exits
        );
    }
}
