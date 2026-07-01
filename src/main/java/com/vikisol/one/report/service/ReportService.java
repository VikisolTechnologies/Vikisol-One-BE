package com.vikisol.one.report.service;

import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
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

        return new DashboardStats(
                totalEmployees, activeCount, newJoineesThisMonth, onNoticeCount,
                departmentWiseCount, genderDistribution, employmentTypeDistribution
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
