package com.vikisol.one.config;

import com.vikisol.one.attendance.entity.Attendance;
import com.vikisol.one.attendance.repository.AttendanceRepository;
import com.vikisol.one.auth.entity.User;
import com.vikisol.one.auth.repository.UserRepository;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.employee.service.BackgroundCheckService;
import com.vikisol.one.employee.service.OnboardingService;
import com.vikisol.one.leave.entity.LeaveBalance;
import com.vikisol.one.leave.entity.LeaveType;
import com.vikisol.one.leave.repository.LeaveBalanceRepository;
import com.vikisol.one.leave.repository.LeaveTypeRepository;
import com.vikisol.one.notification.entity.Notification;
import com.vikisol.one.notification.service.NotificationService;
import com.vikisol.one.security.RoleEnum;
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
    private final OnboardingService onboardingService;
    private final BackgroundCheckService backgroundCheckService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final com.vikisol.one.notification.repository.NotificationRepository notificationRepository;
    private final com.vikisol.one.session.service.ActiveSessionService activeSessionService;
    private final com.vikisol.one.session.service.RefreshTokenService refreshTokenService;

    // 30-day retention - notifications older than this are permanently deleted rather than
    // accumulating forever. Runs daily just after midnight.
    @Scheduled(cron = "0 30 0 * * *")
    @Transactional
    public void purgeOldNotifications() {
        int deleted = notificationRepository.deleteByCreatedAtBefore(java.time.LocalDateTime.now().minusDays(30));
        if (deleted > 0) log.info("Purged {} notification(s) older than 30 days", deleted);
    }

    // Session/refresh-token retention - both tables previously grew unboundedly (every single
    // silent token refresh used to insert a brand-new ActiveSession row rather than reusing the
    // existing device session, see AuthService.refresh/ActiveSessionService.rotateJti), which
    // degraded query performance across the app over time. Expired refresh tokens and old revoked
    // sessions are now permanently deleted daily.
    @Scheduled(cron = "0 45 0 * * *")
    public void purgeStaleSessionData() {
        int refreshTokensDeleted = refreshTokenService.purgeExpired();
        int sessionsDeleted = activeSessionService.purgeRevokedBefore(java.time.LocalDateTime.now().minusDays(30));
        if (refreshTokensDeleted > 0 || sessionsDeleted > 0) {
            log.info("Purged {} expired refresh token(s) and {} revoked session(s) older than 30 days", refreshTokensDeleted, sessionsDeleted);
        }
    }

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

    // Daily nudge so an incomplete profile doesn't just sit silently: the employee gets a direct
    // reminder, and HR/CEO/Admin get one digest notification (not one per employee - that would
    // spam them) summarizing how many people are still pending onboarding/BGV/documents.
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void sendOnboardingReminders() {
        Page<Employee> employees = employeeRepository.findByIsActiveTrue(Pageable.unpaged());
        int pendingOnboarding = 0;
        int pendingBgv = 0;

        for (Employee emp : employees) {
            if (emp.getUser() == null) continue;

            boolean incomplete = onboardingService.getProfileCompletion(emp.getId()).percent() < 100;
            if (incomplete) {
                pendingOnboarding++;
                notificationService.sendNotification(emp.getUser().getId(),
                        "Complete your profile",
                        "Your Vikisol One profile is still incomplete. Please finish the onboarding steps.",
                        Notification.NotificationType.GENERAL, emp.getId(), "EMPLOYEE_ONBOARDING");
            }
            if (!backgroundCheckService.isFullyApproved(emp.getId())) {
                pendingBgv++;
            }
        }

        if (pendingOnboarding > 0 || pendingBgv > 0) {
            String digest = pendingOnboarding + " employee(s) have an incomplete profile, " + pendingBgv + " employee(s) have pending background verification.";
            for (User hr : userRepository.findByRoleIn(List.of(RoleEnum.CEO, RoleEnum.HR_MANAGER, RoleEnum.ADMIN))) {
                notificationService.sendNotification(hr.getId(), "Onboarding status digest", digest,
                        Notification.NotificationType.GENERAL, null, "ONBOARDING_DIGEST");
            }
        }

        log.info("Onboarding reminders sent: {} incomplete profiles, {} pending BGV", pendingOnboarding, pendingBgv);
    }
}
