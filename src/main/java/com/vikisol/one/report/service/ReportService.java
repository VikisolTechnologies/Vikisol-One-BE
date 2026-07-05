package com.vikisol.one.report.service;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final EmployeeRepository employeeRepository;
    private final OnboardingService onboardingService;
    private final BackgroundCheckService backgroundCheckService;
    private final DocumentRepository documentRepository;

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
        // TODO: Integrate with Attendance module when available
        return Collections.emptyList();
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
