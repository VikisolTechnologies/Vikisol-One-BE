package com.vikisol.one.config;

import com.vikisol.one.attendance.entity.Attendance;
import com.vikisol.one.attendance.repository.AttendanceRepository;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.leave.entity.LeaveBalance;
import com.vikisol.one.leave.entity.LeaveType;
import com.vikisol.one.leave.repository.LeaveBalanceRepository;
import com.vikisol.one.leave.repository.LeaveTypeRepository;
import com.vikisol.one.settings.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasks {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final HolidayRepository holidayRepository;

    @Scheduled(cron = "0 0 22 * * MON-FRI")
    @Transactional
    public void markAbsentees() {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return;
        if (holidayRepository.findByDate(today).isPresent()) return;

        Page<Employee> employees = employeeRepository.findByIsActiveTrue(Pageable.unpaged());
        int marked = 0;
        for (Employee emp : employees) {
            if (attendanceRepository.findByEmployeeIdAndDate(emp.getId(), today).isEmpty()) {
                Attendance att = new Attendance();
                att.setEmployee(emp);
                att.setDate(today);
                att.setStatus(Attendance.AttendanceStatus.ABSENT);
                att.setSource(Attendance.AttendanceSource.MANUAL);
                att.setWorkingHours(0);
                att.setOvertimeHours(0);
                attendanceRepository.save(att);
                marked++;
            }
        }
        log.info("Marked {} employees as absent for {}", marked, today);
    }

    @Scheduled(cron = "0 0 0 1 1 *")
    @Transactional
    public void initializeYearlyLeaveBalances() {
        int year = LocalDate.now().getYear();
        List<LeaveType> leaveTypes = leaveTypeRepository.findByIsActiveTrue();
        Page<Employee> employees = employeeRepository.findByIsActiveTrue(Pageable.unpaged());

        int count = 0;
        for (Employee emp : employees) {
            for (LeaveType lt : leaveTypes) {
                if (leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(emp.getId(), lt.getId(), year).isEmpty()) {
                    double carryForward = 0;
                    if (lt.isCarryForward()) {
                        leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(emp.getId(), lt.getId(), year - 1)
                                .ifPresent(prev -> {});
                        var prevBalance = leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(emp.getId(), lt.getId(), year - 1);
                        if (prevBalance.isPresent()) {
                            carryForward = Math.min(prevBalance.get().getRemainingDays(), lt.getMaxCarryForwardDays());
                        }
                    }

                    LeaveBalance balance = new LeaveBalance();
                    balance.setEmployee(emp);
                    balance.setLeaveType(lt);
                    balance.setYear(year);
                    balance.setTotalDays(lt.getDefaultDays() + carryForward);
                    balance.setUsedDays(0);
                    balance.setRemainingDays(lt.getDefaultDays() + carryForward);
                    balance.setCarryForwardDays(carryForward);
                    leaveBalanceRepository.save(balance);
                    count++;
                }
            }
        }
        log.info("Initialized {} leave balances for year {}", count, year);
    }
}
