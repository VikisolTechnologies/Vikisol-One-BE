package com.vikisol.one.attendance.service;

import com.vikisol.one.attendance.dto.*;
import com.vikisol.one.attendance.entity.Attendance;
import com.vikisol.one.attendance.entity.AttendanceRegularization;
import com.vikisol.one.attendance.repository.AttendanceRegularizationRepository;
import com.vikisol.one.attendance.repository.AttendanceRepository;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.leave.dto.LeaveActionRequest;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceRegularizationRepository regularizationRepository;
    private final EmployeeRepository employeeRepository;

    private static final double STANDARD_WORKING_HOURS = 8.0;

    public AttendanceResponse checkIn(UserPrincipal principal, CheckInRequest request) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        LocalDate today = LocalDate.now();
        var existing = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today);

        Attendance attendance;
        if (existing.isPresent()) {
            attendance = existing.get();
            if (attendance.getCheckInTime() != null) {
                throw new RuntimeException("Already checked in for today");
            }
        } else {
            attendance = Attendance.builder()
                    .employee(employee)
                    .date(today)
                    .status(Attendance.AttendanceStatus.PRESENT)
                    .source(Attendance.AttendanceSource.valueOf(request.source()))
                    .build();
        }

        attendance.setCheckInTime(LocalTime.now());
        attendance.setRemarks(request.remarks());
        attendance = attendanceRepository.save(attendance);

        return mapToResponse(attendance);
    }

    public AttendanceResponse checkOut(UserPrincipal principal, CheckOutRequest request) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        LocalDate today = LocalDate.now();
        Attendance attendance = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), today)
                .orElseThrow(() -> new RuntimeException("No check-in found for today"));

        if (attendance.getCheckInTime() == null) {
            throw new RuntimeException("Cannot check out without checking in first");
        }

        if (attendance.getCheckOutTime() != null) {
            throw new RuntimeException("Already checked out for today");
        }

        LocalTime checkOutTime = LocalTime.now();
        attendance.setCheckOutTime(checkOutTime);

        double hours = Duration.between(attendance.getCheckInTime(), checkOutTime).toMinutes() / 60.0;
        attendance.setWorkingHours(Math.round(hours * 100.0) / 100.0);

        if (hours > STANDARD_WORKING_HOURS) {
            attendance.setOvertimeHours(Math.round((hours - STANDARD_WORKING_HOURS) * 100.0) / 100.0);
        }

        if (hours > 0 && hours < 4) {
            attendance.setStatus(Attendance.AttendanceStatus.HALF_DAY);
        }

        if (request.remarks() != null) {
            attendance.setRemarks(request.remarks());
        }

        attendance = attendanceRepository.save(attendance);
        return mapToResponse(attendance);
    }

    @Transactional(readOnly = true)
    public AttendanceResponse getTodayAttendance(UserPrincipal principal) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        return attendanceRepository.findByEmployeeIdAndDate(employee.getId(), LocalDate.now())
                .map(this::mapToResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> getMyAttendance(UserPrincipal principal, LocalDate start, LocalDate end) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        return attendanceRepository.findByEmployeeIdAndDateBetween(employee.getId(), start, end).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonthlyAttendanceSummary getMonthlyAttendanceSummary(UUID employeeId, int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        int totalDays = yearMonth.lengthOfMonth();

        List<Attendance> records = attendanceRepository.findByEmployeeIdAndDateBetween(employeeId, startDate, endDate);

        int presentDays = 0, absentDays = 0, halfDays = 0, leaveDays = 0, holidays = 0, weekends = 0;
        double totalWorking = 0, totalOvertime = 0;

        for (Attendance a : records) {
            switch (a.getStatus()) {
                case PRESENT -> { presentDays++; totalWorking += a.getWorkingHours(); totalOvertime += a.getOvertimeHours(); }
                case ABSENT -> absentDays++;
                case HALF_DAY -> { halfDays++; totalWorking += a.getWorkingHours(); }
                case ON_LEAVE -> leaveDays++;
                case HOLIDAY -> holidays++;
                case WEEKEND -> weekends++;
            }
        }

        // Count weekends in the month that may not have records
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                final LocalDate currentDate = date;
                boolean hasRecord = records.stream().anyMatch(a -> a.getDate().equals(currentDate));
                if (!hasRecord) {
                    weekends++;
                }
            }
            date = date.plusDays(1);
        }

        double avgWorkingHours = presentDays > 0 ? Math.round((totalWorking / presentDays) * 100.0) / 100.0 : 0;

        return new MonthlyAttendanceSummary(
                totalDays, presentDays, absentDays, halfDays, leaveDays,
                holidays, weekends, avgWorkingHours, totalOvertime
        );
    }

    public void requestRegularization(RegularizationRequest request, UserPrincipal principal) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Attendance attendance = attendanceRepository.findById(request.attendanceId())
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        if (!attendance.getEmployee().getId().equals(employee.getId())) {
            throw new RuntimeException("You can only regularize your own attendance");
        }

        AttendanceRegularization regularization = AttendanceRegularization.builder()
                .attendance(attendance)
                .employee(employee)
                .originalStatus(attendance.getStatus())
                .requestedStatus(Attendance.AttendanceStatus.valueOf(request.requestedStatus()))
                .reason(request.reason())
                .status(AttendanceRegularization.RegularizationStatus.PENDING)
                .build();

        regularizationRepository.save(regularization);
    }

    public void processRegularization(UUID regId, LeaveActionRequest request, UserPrincipal principal) {
        AttendanceRegularization regularization = regularizationRepository.findById(regId)
                .orElseThrow(() -> new RuntimeException("Regularization request not found"));

        if (regularization.getStatus() != AttendanceRegularization.RegularizationStatus.PENDING) {
            throw new RuntimeException("Regularization request is not in PENDING status");
        }

        Employee approver = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        if ("APPROVE".equalsIgnoreCase(request.action())) {
            regularization.setStatus(AttendanceRegularization.RegularizationStatus.APPROVED);
            regularization.setApprovedById(approver.getId());
            regularization.setApproverComments(request.comments());

            // Update attendance record
            Attendance attendance = regularization.getAttendance();
            attendance.setStatus(regularization.getRequestedStatus());
            attendance.setRegularized(true);
            attendanceRepository.save(attendance);

        } else if ("REJECT".equalsIgnoreCase(request.action())) {
            regularization.setStatus(AttendanceRegularization.RegularizationStatus.REJECTED);
            regularization.setApprovedById(approver.getId());
            regularization.setApproverComments(request.comments());
        } else {
            throw new RuntimeException("Invalid action. Use APPROVE or REJECT");
        }

        regularizationRepository.save(regularization);
    }

    /**
     * Team-wide attendance for a given date. CEO/HR_MANAGER/ADMIN see the whole company;
     * a MANAGER sees only their direct reports.
     */
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getTeamAttendance(UserPrincipal principal, LocalDate date) {
        boolean isCompanyWide = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));

        List<Attendance> records = attendanceRepository.findByDate(date);

        if (!isCompanyWide) {
            Employee manager = employeeRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
            records = records.stream()
                    .filter(a -> manager.getId().equals(a.getEmployee().getReportingManagerId()))
                    .toList();
        }

        return records.stream().map(this::mapToResponse).toList();
    }

    public void markAbsentees(LocalDate date) {
        List<Employee> allEmployees = employeeRepository.findAll();

        for (Employee employee : allEmployees) {
            if (!employee.isActive()) continue;

            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) continue;

            var existing = attendanceRepository.findByEmployeeIdAndDate(employee.getId(), date);
            if (existing.isEmpty()) {
                Attendance attendance = Attendance.builder()
                        .employee(employee)
                        .date(date)
                        .status(Attendance.AttendanceStatus.ABSENT)
                        .source(Attendance.AttendanceSource.MANUAL)
                        .build();
                attendanceRepository.save(attendance);
            }
        }
    }

    // --- Helper ---

    private AttendanceResponse mapToResponse(Attendance a) {
        Employee emp = a.getEmployee();
        // Still checked in (no checkout yet) - the persisted workingHours stays 0 until checkout,
        // so compute the in-progress duration on the fly instead of reporting a stale 0 to the client.
        double workingHours = a.getWorkingHours();
        if (a.getCheckInTime() != null && a.getCheckOutTime() == null) {
            workingHours = Math.round(Duration.between(a.getCheckInTime(), LocalTime.now()).toMinutes() / 60.0 * 100.0) / 100.0;
        }
        return new AttendanceResponse(
                a.getId(),
                emp.getFirstName() + " " + emp.getLastName(),
                emp.getEmployeeId(),
                a.getDate(),
                a.getCheckInTime(),
                a.getCheckOutTime(),
                a.getStatus().name(),
                workingHours,
                a.getOvertimeHours(),
                a.getSource().name(),
                a.isRegularized()
        );
    }
}
