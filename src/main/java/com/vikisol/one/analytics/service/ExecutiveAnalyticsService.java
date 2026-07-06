package com.vikisol.one.analytics.service;

import com.vikisol.one.analytics.dto.ExecutiveAnalyticsResponse;
import com.vikisol.one.analytics.dto.ExecutiveAnalyticsResponse.*;
import com.vikisol.one.asset.entity.Asset;
import com.vikisol.one.asset.repository.AssetRepository;
import com.vikisol.one.employee.entity.BackgroundCheck;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.BackgroundCheckRepository;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.leave.entity.LeaveRequest;
import com.vikisol.one.leave.entity.LeaveBalance;
import com.vikisol.one.leave.repository.LeaveBalanceRepository;
import com.vikisol.one.leave.repository.LeaveRequestRepository;
import com.vikisol.one.payroll.entity.Payslip;
import com.vikisol.one.payroll.repository.PayslipRepository;
import com.vikisol.one.recruitment.entity.Candidate;
import com.vikisol.one.recruitment.entity.Interview;
import com.vikisol.one.recruitment.repository.CandidateRepository;
import com.vikisol.one.recruitment.repository.InterviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

// Real-data aggregation service for the CEO/HR executive analytics dashboard. Follows
// ReportService's pragmatic style: findAll() + in-memory Java-stream aggregation rather than
// hand-rolled native aggregate queries - this codebase's data volume (dozens/hundreds of rows per
// table) does not warrant more than that, and HrTaskCenterService already sets this precedent.
@Service
@RequiredArgsConstructor
public class ExecutiveAnalyticsService {

    private final EmployeeRepository employeeRepository;
    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final BackgroundCheckRepository backgroundCheckRepository;
    private final AssetRepository assetRepository;
    private final PayslipRepository payslipRepository;

    public ExecutiveAnalyticsResponse getExecutiveAnalytics() {
        List<Employee> allEmployees = employeeRepository.findAll();
        LocalDate today = LocalDate.now();

        return new ExecutiveAnalyticsResponse(
                buildRecruitmentAnalytics(allEmployees),
                buildWorkforceAnalytics(allEmployees),
                buildPayrollAnalytics(allEmployees, today.getMonthValue(), today.getYear()),
                buildLeaveAnalytics(),
                buildBgvAnalytics(),
                buildAssetAnalytics()
        );
    }

    // ---------------------------------------------------------------- Recruitment

    private RecruitmentAnalytics buildRecruitmentAnalytics(List<Employee> allEmployees) {
        List<Candidate> candidates = candidateRepository.findAll();

        Map<String, Long> hiringFunnel = candidates.stream()
                .collect(Collectors.groupingBy(c -> c.getStatus().name(), Collectors.counting()));

        long totalCandidates = candidates.size();
        long joined = candidates.stream().filter(c -> c.getStatus() == Candidate.Status.JOINED).count();
        double candidateConversionRate = totalCandidates == 0 ? 0
                : round2(joined * 100.0 / totalCandidates);

        // Interview conversion: distinct candidates who had >=1 interview vs those among them who
        // reached an offer stage or beyond.
        List<Interview> interviews = interviewRepository.findAll();
        Set<UUID> candidatesWithInterview = interviews.stream()
                .filter(i -> i.getCandidate() != null)
                .map(i -> i.getCandidate().getId())
                .collect(Collectors.toSet());
        Set<Candidate.Status> offerOrBeyond = EnumSet.of(Candidate.Status.OFFER_MADE, Candidate.Status.OFFER_ACCEPTED,
                Candidate.Status.OFFER_DECLINED, Candidate.Status.JOINED);
        long candidatesReachingOffer = candidates.stream()
                .filter(c -> candidatesWithInterview.contains(c.getId()) && offerOrBeyond.contains(c.getStatus()))
                .count();
        double interviewConversionRate = candidatesWithInterview.isEmpty() ? 0
                : round2(candidatesReachingOffer * 100.0 / candidatesWithInterview.size());

        long offersExtended = candidates.stream().filter(c -> offerOrBeyond.contains(c.getStatus())).count();
        long offersAccepted = candidates.stream()
                .filter(c -> c.getStatus() == Candidate.Status.OFFER_ACCEPTED || c.getStatus() == Candidate.Status.JOINED)
                .count();
        double offerAcceptanceRate = offersExtended == 0 ? 0 : round2(offersAccepted * 100.0 / offersExtended);

        List<Candidate> joinedCandidates = candidates.stream()
                .filter(c -> c.getStatus() == Candidate.Status.JOINED && c.getCreatedAt() != null && c.getOfferedDateOfJoining() != null)
                .collect(Collectors.toList());
        Double avgTimeToHireDays = joinedCandidates.isEmpty() ? null
                : round2(joinedCandidates.stream()
                        .mapToLong(c -> ChronoUnit.DAYS.between(c.getCreatedAt().toLocalDate(), c.getOfferedDateOfJoining()))
                        .filter(d -> d >= 0)
                        .average().orElse(0));

        Map<UUID, Employee> employeesById = allEmployees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));

        List<RecruiterPerformance> recruiterPerformance = candidates.stream()
                .filter(c -> c.getAssignedRecruiterId() != null)
                .collect(Collectors.groupingBy(Candidate::getAssignedRecruiterId))
                .entrySet().stream()
                .map(entry -> {
                    Employee recruiter = employeesById.get(entry.getKey());
                    String name = recruiter != null ? recruiter.getFirstName() + " " + recruiter.getLastName() : "Unknown";
                    long handled = entry.getValue().size();
                    long hires = entry.getValue().stream().filter(c -> c.getStatus() == Candidate.Status.JOINED).count();
                    return new RecruiterPerformance(name, handled, hires);
                })
                .sorted(Comparator.comparingLong(RecruiterPerformance::hires).reversed())
                .collect(Collectors.toList());

        List<HiringManagerPerformance> hiringManagerPerformance = candidates.stream()
                .filter(c -> c.getHiringManagerId() != null)
                .collect(Collectors.groupingBy(Candidate::getHiringManagerId))
                .entrySet().stream()
                .map(entry -> {
                    Employee manager = employeesById.get(entry.getKey());
                    String name = manager != null ? manager.getFirstName() + " " + manager.getLastName() : "Unknown";
                    long handled = entry.getValue().size();
                    long hires = entry.getValue().stream().filter(c -> c.getStatus() == Candidate.Status.JOINED).count();
                    return new HiringManagerPerformance(name, handled, hires);
                })
                .sorted(Comparator.comparingLong(HiringManagerPerformance::hires).reversed())
                .collect(Collectors.toList());

        return new RecruitmentAnalytics(hiringFunnel, candidateConversionRate, interviewConversionRate,
                offerAcceptanceRate, avgTimeToHireDays, recruiterPerformance, hiringManagerPerformance);
    }

    // ---------------------------------------------------------------- Workforce

    private WorkforceAnalytics buildWorkforceAnalytics(List<Employee> allEmployees) {
        List<Employee> activeEmployees = allEmployees.stream().filter(Employee::isActive).collect(Collectors.toList());

        Map<String, Long> lifecycleStatusDistribution = allEmployees.stream()
                .filter(e -> e.getLifecycleStatus() != null)
                .collect(Collectors.groupingBy(e -> e.getLifecycleStatus().name(), Collectors.counting()));

        long probationCount = lifecycleStatusDistribution.getOrDefault(Employee.LifecycleStatus.PROBATION.name(), 0L);
        long noticePeriodCount = lifecycleStatusDistribution.getOrDefault(Employee.LifecycleStatus.NOTICE_PERIOD.name(), 0L);
        long offboardingCount = lifecycleStatusDistribution.getOrDefault(Employee.LifecycleStatus.OFFBOARDING.name(), 0L);

        LocalDate oneYearAgo = LocalDate.now().minusMonths(12);
        long exitedLast12Months = allEmployees.stream()
                .filter(e -> (e.getLifecycleStatus() == Employee.LifecycleStatus.EXITED || e.getLifecycleStatus() == Employee.LifecycleStatus.ALUMNI)
                        && e.getUpdatedAt() != null && !e.getUpdatedAt().toLocalDate().isBefore(oneYearAgo))
                .count();
        double avgHeadcount = (allEmployees.size() + activeEmployees.size()) / 2.0;
        Double attritionRatePercent = avgHeadcount == 0 ? null : round2(exitedLast12Months * 100.0 / avgHeadcount);

        DateTimeFormatter ym = DateTimeFormatter.ofPattern("yyyy-MM");
        Map<String, Long> headcountGrowthByMonth = new TreeMap<>();
        LocalDate cursor = LocalDate.now().minusMonths(11).withDayOfMonth(1);
        for (int i = 0; i < 12; i++) {
            LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            long joinedByThen = allEmployees.stream()
                    .filter(e -> e.getDateOfJoining() != null && !e.getDateOfJoining().isAfter(monthEnd))
                    .count();
            headcountGrowthByMonth.put(cursor.format(ym), joinedByThen);
            cursor = cursor.plusMonths(1);
        }

        Map<String, Long> departmentDistribution = activeEmployees.stream()
                .filter(e -> e.getDepartment() != null)
                .collect(Collectors.groupingBy(e -> e.getDepartment().getName(), Collectors.counting()));

        Map<String, Long> locationDistribution = activeEmployees.stream()
                .filter(e -> e.getCity() != null && !e.getCity().isBlank())
                .collect(Collectors.groupingBy(Employee::getCity, Collectors.counting()));

        return new WorkforceAnalytics(
                allEmployees.size(), activeEmployees.size(), probationCount, noticePeriodCount, offboardingCount,
                lifecycleStatusDistribution, attritionRatePercent, headcountGrowthByMonth,
                departmentDistribution, locationDistribution
        );
    }

    // ---------------------------------------------------------------- Payroll

    private PayrollAnalytics buildPayrollAnalytics(List<Employee> allEmployees, int month, int year) {
        List<Payslip> currentMonthPayslips = payslipRepository.findByMonthAndYear(month, year);

        BigDecimal monthlyPayrollTotal = currentMonthPayslips.stream()
                .map(p -> p.getNetSalary() == null ? BigDecimal.ZERO : p.getNetSalary())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate prevMonthDate = LocalDate.of(year, month, 1).minusMonths(1);
        List<Payslip> prevMonthPayslips = payslipRepository.findByMonthAndYear(prevMonthDate.getMonthValue(), prevMonthDate.getYear());
        BigDecimal prevTotal = prevMonthPayslips.stream()
                .map(p -> p.getNetSalary() == null ? BigDecimal.ZERO : p.getNetSalary())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Double payrollGrowthPercent = prevTotal.compareTo(BigDecimal.ZERO) == 0 ? null
                : round2(monthlyPayrollTotal.subtract(prevTotal).divide(prevTotal, 6, RoundingMode.HALF_UP).doubleValue() * 100.0);

        Map<UUID, Employee> employeesById = allEmployees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));

        Map<String, BigDecimal> costPerDepartment = currentMonthPayslips.stream()
                .filter(p -> p.getEmployee() != null)
                .collect(Collectors.groupingBy(
                        p -> {
                            Employee emp = employeesById.get(p.getEmployee().getId());
                            return emp != null && emp.getDepartment() != null ? emp.getDepartment().getName() : "Unassigned";
                        },
                        Collectors.reducing(BigDecimal.ZERO, p -> p.getNetSalary() == null ? BigDecimal.ZERO : p.getNetSalary(), BigDecimal::add)
                ));

        List<Employee> activeWithCtc = allEmployees.stream()
                .filter(e -> e.isActive() && e.getCtc() != null)
                .collect(Collectors.toList());
        BigDecimal highestSalary = activeWithCtc.stream().map(Employee::getCtc).max(BigDecimal::compareTo).orElse(null);
        BigDecimal averageSalary = activeWithCtc.isEmpty() ? null
                : activeWithCtc.stream().map(Employee::getCtc).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(activeWithCtc.size()), 2, RoundingMode.HALF_UP);

        Map<String, Long> salaryDistributionBuckets = new LinkedHashMap<>();
        salaryDistributionBuckets.put("< 3L", 0L);
        salaryDistributionBuckets.put("3L - 6L", 0L);
        salaryDistributionBuckets.put("6L - 10L", 0L);
        salaryDistributionBuckets.put("10L - 15L", 0L);
        salaryDistributionBuckets.put("15L+", 0L);
        for (Employee e : activeWithCtc) {
            double ctc = e.getCtc().doubleValue();
            String bucket = ctc < 300000 ? "< 3L"
                    : ctc < 600000 ? "3L - 6L"
                    : ctc < 1000000 ? "6L - 10L"
                    : ctc < 1500000 ? "10L - 15L" : "15L+";
            salaryDistributionBuckets.merge(bucket, 1L, Long::sum);
        }

        return new PayrollAnalytics(month, year, monthlyPayrollTotal, payrollGrowthPercent,
                costPerDepartment, highestSalary, averageSalary, salaryDistributionBuckets);
    }

    // ---------------------------------------------------------------- Leave

    private LeaveAnalytics buildLeaveAnalytics() {
        List<LeaveRequest> allRequests = leaveRequestRepository.findAll();
        DateTimeFormatter ym = DateTimeFormatter.ofPattern("yyyy-MM");

        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(5).withDayOfMonth(1);
        Map<String, Long> leaveRequestsByMonth = new TreeMap<>();
        LocalDate cursor = sixMonthsAgo;
        for (int i = 0; i < 6; i++) {
            final LocalDate monthStart = cursor;
            final LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            long count = allRequests.stream()
                    .filter(r -> r.getAppliedOn() != null
                            && !r.getAppliedOn().toLocalDate().isBefore(monthStart)
                            && !r.getAppliedOn().toLocalDate().isAfter(monthEnd))
                    .count();
            leaveRequestsByMonth.put(monthStart.format(ym), count);
            cursor = cursor.plusMonths(1);
        }

        Map<String, Double> leaveDaysByType = allRequests.stream()
                .filter(r -> r.getStatus() == LeaveRequest.LeaveStatus.APPROVED && r.getLeaveType() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getLeaveType().getName(),
                        Collectors.summingDouble(LeaveRequest::getNumberOfDays)
                ));

        List<LeaveBalance> allBalances = leaveBalanceRepository.findAll();
        double totalRemainingBalance = allBalances.stream().mapToDouble(LeaveBalance::getRemainingDays).sum();
        double totalUsedBalance = allBalances.stream().mapToDouble(LeaveBalance::getUsedDays).sum();

        return new LeaveAnalytics(leaveRequestsByMonth, leaveDaysByType, round2(totalRemainingBalance), round2(totalUsedBalance));
    }

    // ---------------------------------------------------------------- BGV

    private BgvAnalytics buildBgvAnalytics() {
        List<BackgroundCheck> allChecks = backgroundCheckRepository.findAll();

        long pending = allChecks.stream()
                .filter(c -> c.getStatus() != BackgroundCheck.Status.APPROVED && c.getStatus() != BackgroundCheck.Status.REJECTED)
                .count();
        long cleared = allChecks.stream().filter(c -> c.getStatus() == BackgroundCheck.Status.APPROVED).count();
        long failed = allChecks.stream().filter(c -> c.getStatus() == BackgroundCheck.Status.REJECTED).count();

        List<BackgroundCheck> reviewed = allChecks.stream()
                .filter(c -> c.getReviewedAt() != null && c.getCreatedAt() != null)
                .collect(Collectors.toList());
        Double avgCompletionDays = reviewed.isEmpty() ? null
                : round2(reviewed.stream()
                        .mapToLong(c -> ChronoUnit.HOURS.between(c.getCreatedAt(), c.getReviewedAt()))
                        .average().orElse(0) / 24.0);

        return new BgvAnalytics(pending, cleared, failed, avgCompletionDays);
    }

    // ---------------------------------------------------------------- Assets

    private AssetAnalytics buildAssetAnalytics() {
        List<Asset> allAssets = assetRepository.findAll();

        long allocated = allAssets.stream().filter(a -> a.getStatus() == Asset.Status.ASSIGNED).count();
        long available = allAssets.stream().filter(a -> a.getStatus() == Asset.Status.AVAILABLE).count();
        long lost = allAssets.stream().filter(a -> a.getStatus() == Asset.Status.LOST).count();
        long underMaintenance = allAssets.stream().filter(a -> a.getStatus() == Asset.Status.IN_REPAIR).count();
        long retired = allAssets.stream().filter(a -> a.getStatus() == Asset.Status.RETIRED).count();

        Map<String, Long> byCategory = allAssets.stream()
                .collect(Collectors.groupingBy(a -> a.getCategory().name(), Collectors.counting()));

        return new AssetAnalytics(allocated, available, lost, underMaintenance, retired, byCategory);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
