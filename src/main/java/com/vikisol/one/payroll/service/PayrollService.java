package com.vikisol.one.payroll.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.doctemplate.service.DocumentGenerationService;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.payroll.dto.*;
import com.vikisol.one.payroll.entity.PayrollConfig;
import com.vikisol.one.payroll.entity.Payslip;
import com.vikisol.one.payroll.entity.SalaryAdvance;
import com.vikisol.one.payroll.repository.PayrollConfigRepository;
import com.vikisol.one.payroll.repository.PayslipRepository;
import com.vikisol.one.payroll.repository.SalaryAdvanceRepository;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PayrollService {

    private final PayrollConfigRepository payrollConfigRepository;
    private final PayslipRepository payslipRepository;
    private final SalaryAdvanceRepository salaryAdvanceRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;
    private final DocumentGenerationService documentGenerationService;

    // ── Config ──────────────────────────────────────────────────────────────

    public String getConfigValue(String key) {
        return payrollConfigRepository.findByKey(key)
                .map(PayrollConfig::getValue)
                .orElseThrow(() -> new EntityNotFoundException("Config not found: " + key));
    }

    public BigDecimal getConfigAsBigDecimal(String key) {
        return new BigDecimal(getConfigValue(key));
    }

    public PayrollConfig updateConfig(PayrollConfigRequest request) {
        PayrollConfig config = payrollConfigRepository.findByKey(request.key())
                .orElse(PayrollConfig.builder()
                        .key(request.key())
                        .build());
        config.setValue(request.value());
        config.setDescription(request.description());
        config.setCategory(request.category());
        return payrollConfigRepository.save(config);
    }

    public List<PayrollConfig> getAllConfigs() {
        return payrollConfigRepository.findAll();
    }

    // ── CTC Breakup Template (CEO-defined, applies to every offer/employee) ──

    private static final String CTC_BREAKUP_CATEGORY = "CTC_BREAKUP";
    private static final String CUSTOM_LABEL_KEY = "CUSTOM_ALLOWANCE_LABEL";
    private static final java.util.Map<String, BigDecimal> DEFAULT_CTC_BREAKUP = java.util.Map.of(
            "BASIC_PCT", new BigDecimal("50"),
            "HRA_PCT", new BigDecimal("20"),
            "CONVEYANCE_PCT", new BigDecimal("10"),
            "MEDICAL_PCT", new BigDecimal("10"),
            "SPECIAL_PCT", new BigDecimal("10"),
            // CEO-nameable 6th component - 0% by default so existing breakups are unaffected
            // until the CEO explicitly assigns it a percentage.
            "CUSTOM_PCT", BigDecimal.ZERO
    );

    // The CEO-chosen name for the custom component (e.g. "LTA", "Bonus"). Stored as a
    // PayrollConfig row outside the percentage map since it's text, not a number.
    public String getCtcCustomLabel() {
        return payrollConfigRepository.findByKey(CUSTOM_LABEL_KEY)
                .map(PayrollConfig::getValue)
                .orElse("Custom Allowance");
    }

    public String updateCtcCustomLabel(String label) {
        PayrollConfig config = payrollConfigRepository.findByKey(CUSTOM_LABEL_KEY)
                .orElse(PayrollConfig.builder().key(CUSTOM_LABEL_KEY).build());
        config.setValue(label);
        config.setCategory(CTC_BREAKUP_CATEGORY);
        config.setDescription("Label for the CEO-defined custom CTC component");
        payrollConfigRepository.save(config);
        return label;
    }

    public java.util.Map<String, BigDecimal> getCtcBreakupTemplate() {
        List<PayrollConfig> saved = payrollConfigRepository.findByCategory(CTC_BREAKUP_CATEGORY);
        java.util.Map<String, BigDecimal> result = new java.util.LinkedHashMap<>(DEFAULT_CTC_BREAKUP);
        for (PayrollConfig c : saved) {
            if (CUSTOM_LABEL_KEY.equals(c.getKey())) continue; // text label, not a percentage
            result.put(c.getKey(), new BigDecimal(c.getValue()));
        }
        return result;
    }

    public java.util.Map<String, BigDecimal> updateCtcBreakupTemplate(java.util.Map<String, BigDecimal> percentages) {
        BigDecimal total = percentages.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(new BigDecimal("100")) != 0) {
            throw new IllegalArgumentException("CTC breakup percentages must add up to 100, got " + total);
        }
        for (var entry : percentages.entrySet()) {
            PayrollConfig config = payrollConfigRepository.findByKey(entry.getKey())
                    .orElse(PayrollConfig.builder().key(entry.getKey()).build());
            config.setValue(entry.getValue().toPlainString());
            config.setCategory(CTC_BREAKUP_CATEGORY);
            config.setDescription("Standard CTC breakup percentage");
            payrollConfigRepository.save(config);
        }
        return getCtcBreakupTemplate();
    }

    /**
     * Splits an annual CTC into salary components using the CEO-defined standard breakup,
     * returning monthly figures the same way employee.basicSalary/hra/etc. are stored.
     */
    public java.util.Map<String, BigDecimal> computeCtcBreakup(BigDecimal annualCtc) {
        java.util.Map<String, BigDecimal> template = getCtcBreakupTemplate();
        java.util.Map<String, BigDecimal> breakup = new java.util.LinkedHashMap<>();
        BigDecimal monthlyCtc = annualCtc.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        breakup.put("basicSalary", pctOf(monthlyCtc, template.get("BASIC_PCT")));
        breakup.put("hra", pctOf(monthlyCtc, template.get("HRA_PCT")));
        breakup.put("conveyanceAllowance", pctOf(monthlyCtc, template.get("CONVEYANCE_PCT")));
        breakup.put("medicalAllowance", pctOf(monthlyCtc, template.get("MEDICAL_PCT")));
        breakup.put("specialAllowance", pctOf(monthlyCtc, template.get("SPECIAL_PCT")));
        breakup.put("customAllowance", pctOf(monthlyCtc, template.getOrDefault("CUSTOM_PCT", BigDecimal.ZERO)));
        breakup.put("grossSalary", monthlyCtc);
        breakup.put("ctc", annualCtc);
        return breakup;
    }

    private BigDecimal pctOf(BigDecimal amount, BigDecimal pct) {
        return amount.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    // ── Payroll Run ─────────────────────────────────────────────────────────

    public PayrollSummaryResponse runPayroll(PayrollRunRequest request) {
        int month = request.month();
        int year = request.year();

        // Get config values
        BigDecimal pfRate = getConfigAsBigDecimal("PF_EMPLOYEE_RATE").divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal pfEmployerRate = getConfigAsBigDecimal("PF_EMPLOYER_RATE").divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal esiEmployeeRate = getConfigAsBigDecimal("ESI_EMPLOYEE_RATE").divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal esiEmployerRate = getConfigAsBigDecimal("ESI_EMPLOYER_RATE").divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal esiThreshold = getConfigAsBigDecimal("ESI_THRESHOLD");
        BigDecimal professionalTax = getConfigAsBigDecimal("PROFESSIONAL_TAX");
        BigDecimal pfCap = BigDecimal.valueOf(15000);

        int workingDays = getWorkingDaysInMonth(year, month);
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = YearMonth.of(year, month).atEndOfMonth();

        // Get all active employees
        List<Employee> employees = employeeRepository.findByEmploymentStatus(Employee.EmploymentStatus.ACTIVE);

        List<Payslip> payslips = new ArrayList<>();

        for (Employee employee : employees) {
            if (!employee.isActive()) continue;

            // Check if payslip already exists
            if (payslipRepository.findByEmployeeIdAndMonthAndYear(employee.getId(), month, year).isPresent()) {
                continue;
            }

            BigDecimal basic = defaultZero(employee.getBasicSalary());
            BigDecimal hraVal = defaultZero(employee.getHra());
            BigDecimal conveyance = defaultZero(employee.getConveyanceAllowance());
            BigDecimal medical = defaultZero(employee.getMedicalAllowance());
            BigDecimal special = defaultZero(employee.getSpecialAllowance());
            BigDecimal custom = defaultZero(employee.getCustomAllowance());
            BigDecimal gross = defaultZero(employee.getGrossSalary());

            // Attendance: assume full attendance if no attendance module yet
            int presentDays = workingDays; // TODO: integrate with AttendanceRepository
            int lopDays = Math.max(0, workingDays - presentDays);
            int paidDays = workingDays - lopDays;

            // LOP deduction
            BigDecimal perDaySalary = workingDays > 0
                    ? gross.divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal lopDeduction = perDaySalary.multiply(BigDecimal.valueOf(lopDays));

            // Gross earnings after LOP
            BigDecimal grossEarnings = gross.subtract(lopDeduction);

            // PF (employee) - capped at 15000 basic
            BigDecimal pfBasic = basic.min(pfCap);
            BigDecimal pfEmployee = pfBasic.multiply(pfRate).setScale(0, RoundingMode.CEILING);

            // ESI (employee)
            BigDecimal esiEmployee = BigDecimal.ZERO;
            if (gross.compareTo(esiThreshold) <= 0) {
                esiEmployee = gross.multiply(esiEmployeeRate).setScale(0, RoundingMode.CEILING);
            }

            // TDS
            BigDecimal annualGross = gross.multiply(BigDecimal.valueOf(12));
            BigDecimal monthlyTds = calculateTDS(annualGross).divide(BigDecimal.valueOf(12), 0, RoundingMode.CEILING);

            // Professional tax is a flat, config-driven monthly charge (e.g. Rs 200) - it must
            // never apply to an employee with no salary structure configured yet (gross = 0,
            // typically an incomplete profile). Without this guard, every other deduction here
            // naturally comes out to 0 when gross is 0, but this flat one didn't, so a payslip
            // could still be generated with a genuinely negative net salary (confirmed live:
            // "-Rs 200.00" on the Payroll dashboard's Lowest KPI).
            BigDecimal effectiveProfessionalTax = gross.signum() > 0 ? professionalTax : BigDecimal.ZERO;

            // Total deductions (including LOP)
            BigDecimal totalDeductions = pfEmployee
                    .add(esiEmployee)
                    .add(effectiveProfessionalTax)
                    .add(monthlyTds)
                    .add(lopDeduction);

            // Net salary - floored at zero as a second line of defense; a negative "salary" is
            // never a valid figure to show or pay out regardless of which deduction caused it.
            BigDecimal netSalary = grossEarnings.subtract(pfEmployee).subtract(esiEmployee)
                    .subtract(effectiveProfessionalTax).subtract(monthlyTds).max(BigDecimal.ZERO);

            // Employer contributions
            BigDecimal pfEmployer = pfBasic.multiply(pfEmployerRate).setScale(0, RoundingMode.CEILING);
            BigDecimal esiEmployer = BigDecimal.ZERO;
            if (gross.compareTo(esiThreshold) <= 0) {
                esiEmployer = gross.multiply(esiEmployerRate).setScale(0, RoundingMode.CEILING);
            }

            Payslip payslip = Payslip.builder()
                    .employee(employee)
                    .month(month)
                    .year(year)
                    .basicSalary(basic)
                    .hra(hraVal)
                    .conveyanceAllowance(conveyance)
                    .medicalAllowance(medical)
                    .specialAllowance(special)
                    .otherEarnings(custom)
                    .grossEarnings(grossEarnings)
                    .pfEmployee(pfEmployee)
                    .esiEmployee(esiEmployee)
                    .professionalTax(professionalTax)
                    .tds(monthlyTds)
                    .lopDeduction(lopDeduction)
                    .otherDeductions(BigDecimal.ZERO)
                    .totalDeductions(totalDeductions)
                    .netSalary(netSalary)
                    .lopDays(lopDays)
                    .workingDays(workingDays)
                    .presentDays(presentDays)
                    .paidDays(paidDays)
                    .pfEmployer(pfEmployer)
                    .esiEmployer(esiEmployer)
                    .status(Payslip.PayslipStatus.DRAFT)
                    .processedDate(LocalDateTime.now())
                    .build();

            payslips.add(payslip);
        }

        payslipRepository.saveAll(payslips);

        auditService.record("Payroll Generated", month + "/" + year,
                payslips.size() + " payslips generated");

        return buildSummary(month, year, payslips);
    }

    // ── Approve / Pay ───────────────────────────────────────────────────────

    public void approvePayroll(int month, int year, UserPrincipal principal) {
        List<Payslip> payslips = payslipRepository.findByMonthAndYearAndStatus(month, year, Payslip.PayslipStatus.DRAFT);
        payslips.forEach(p -> {
            p.setStatus(Payslip.PayslipStatus.APPROVED);
            p.setApprovedById(principal.getId());
        });
        payslipRepository.saveAll(payslips);
    }

    public void markAsPaid(int month, int year, String transactionRef) {
        List<Payslip> payslips = payslipRepository.findByMonthAndYearAndStatus(month, year, Payslip.PayslipStatus.APPROVED);
        payslips.forEach(p -> {
            p.setStatus(Payslip.PayslipStatus.PAID);
            p.setPaidDate(LocalDateTime.now());
            p.setTransactionReference(transactionRef);
        });
        payslipRepository.saveAll(payslips);
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PayslipResponse getPayslip(UUID employeeId, int month, int year) {
        Payslip payslip = payslipRepository.findByEmployeeIdAndMonthAndYear(employeeId, month, year)
                .orElseThrow(() -> new EntityNotFoundException("Payslip not found"));
        return toPayslipResponse(payslip);
    }

    // Latest payslip for an employeeId directly - used by the dashboard aggregator, which already
    // knows the employeeId rather than a UserPrincipal.
    @Transactional(readOnly = true)
    public PayslipResponse getLatestPayslip(UUID employeeId) {
        return payslipRepository.findByEmployeeIdOrderByYearDescMonthDesc(employeeId, org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().stream().findFirst().map(this::toPayslipResponse).orElse(null);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PayslipResponse> getMyPayslips(UserPrincipal principal, Pageable pageable) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        Page<Payslip> page = payslipRepository.findByEmployeeIdOrderByYearDescMonthDesc(employee.getId(), pageable);
        List<PayslipResponse> content = page.getContent().stream().map(this::toPayslipResponse).toList();

        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    // HR/Finance/CEO/Admin-only "all payslips" register - previously the only listing endpoint
    // was getMyPayslips (self-scoped), so the admin Payroll page was actually only ever showing
    // the logged-in caller's own payslips, never the whole company's, even though "Run Payroll"
    // itself genuinely does generate one per active employee.
    @Transactional(readOnly = true)
    public PagedResponse<PayslipResponse> getAllPayslips(Pageable pageable) {
        Page<Payslip> page = payslipRepository.findAllByOrderByYearDescMonthDesc(pageable);
        List<PayslipResponse> content = page.getContent().stream().map(this::toPayslipResponse).toList();
        return new PagedResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    // Builds a real branded PDF for a payslip via the Document Studio engine, stores it, and
    // returns the download URL. principal is used only to enforce that an EMPLOYEE-role caller
    // can only ever generate their own payslip; HR/Finance/CEO/Admin can generate anyone's.
    public String generatePayslipPdf(UUID payslipId, UserPrincipal principal) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new EntityNotFoundException("Payslip not found"));
        Employee employee = payslip.getEmployee();

        boolean isPrivileged = principal.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER")
                        || a.getAuthority().equals("ROLE_FINANCE") || a.getAuthority().equals("ROLE_ADMIN"));
        if (!isPrivileged) {
            Employee callerEmployee = employeeRepository.findByUserId(principal.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
            if (!callerEmployee.getId().equals(employee.getId())) {
                throw new RuntimeException("You can only download your own payslip");
            }
        }

        String fullName = employee.getFirstName() + " " + employee.getLastName();
        String departmentName = employee.getDepartment() != null ? employee.getDepartment().getName() : "";
        String designationTitle = employee.getDesignation() != null ? employee.getDesignation().getTitle() : "";
        String monthName = java.time.Month.of(payslip.getMonth()).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);

        java.util.Map<String, java.math.BigDecimal> earnings = new java.util.LinkedHashMap<>();
        earnings.put("Basic Salary", payslip.getBasicSalary());
        earnings.put("HRA", payslip.getHra());
        earnings.put("Conveyance Allowance", payslip.getConveyanceAllowance());
        earnings.put("Medical Allowance", payslip.getMedicalAllowance());
        earnings.put("Special Allowance", payslip.getSpecialAllowance());
        if (defaultZero(payslip.getOtherEarnings()).compareTo(BigDecimal.ZERO) > 0) {
            earnings.put("Other Earnings", payslip.getOtherEarnings());
        }

        java.util.Map<String, java.math.BigDecimal> deductions = new java.util.LinkedHashMap<>();
        deductions.put("Provident Fund", payslip.getPfEmployee());
        if (defaultZero(payslip.getEsiEmployee()).compareTo(BigDecimal.ZERO) > 0) {
            deductions.put("ESI", payslip.getEsiEmployee());
        }
        deductions.put("Professional Tax", payslip.getProfessionalTax());
        deductions.put("Income Tax (TDS)", payslip.getTds());
        if (defaultZero(payslip.getLopDeduction()).compareTo(BigDecimal.ZERO) > 0) {
            deductions.put("LOP (" + payslip.getLopDays() + " days)", payslip.getLopDeduction());
        }

        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("EmployeeName", fullName);
        fields.put("EmployeeID", employee.getEmployeeId());
        fields.put("Department", departmentName);
        fields.put("Designation", designationTitle);
        fields.put("PayPeriod", monthName + " " + payslip.getYear());
        fields.put("WorkingDays", String.valueOf(payslip.getWorkingDays()));
        fields.put("PaidDays", String.valueOf(payslip.getPaidDays()));
        fields.put("EarningsRows", buildAmountRows(earnings));
        fields.put("DeductionsRows", buildAmountRows(deductions));
        fields.put("GrossEarnings", payslip.getGrossEarnings().toPlainString());
        fields.put("TotalDeductions", payslip.getTotalDeductions().toPlainString());
        fields.put("NetSalary", payslip.getNetSalary().toPlainString());

        String title = "Payslip - " + monthName + " " + payslip.getYear();
        String fileUrl = documentGenerationService.generateAndStore(Document.DocumentType.PAYSLIP, fields, employee, title);
        auditService.record("Payslip PDF Generated", employee.getEmployeeId(), title);
        return fileUrl;
    }

    private String buildAmountRows(java.util.Map<String, java.math.BigDecimal> rows) {
        StringBuilder sb = new StringBuilder();
        rows.forEach((label, amount) -> sb.append("<tr><td style=\"padding:4px 0;font-size:11px;color:#444;\">")
                .append(label)
                .append("</td><td style=\"padding:4px 0;font-size:11px;text-align:right;\">Rs. ")
                .append(amount)
                .append("</td></tr>"));
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public PayrollSummaryResponse getPayrollSummary(int month, int year) {
        List<Payslip> payslips = payslipRepository.findByMonthAndYear(month, year);
        if (payslips.isEmpty()) {
            throw new EntityNotFoundException("No payroll data found for " + month + "/" + year);
        }
        return buildSummary(month, year, payslips);
    }

    // ── Salary Advance ──────────────────────────────────────────────────────

    public SalaryAdvanceResponse requestSalaryAdvance(SalaryAdvanceRequest request, UserPrincipal principal) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));

        BigDecimal emiAmount = request.amount()
                .divide(BigDecimal.valueOf(request.emiMonths()), 2, RoundingMode.CEILING);

        SalaryAdvance advance = SalaryAdvance.builder()
                .employee(employee)
                .amount(request.amount())
                .requestDate(LocalDate.now())
                .reason(request.reason())
                .status(SalaryAdvance.AdvanceStatus.PENDING)
                .emiMonths(request.emiMonths())
                .emiAmount(emiAmount)
                .remainingAmount(request.amount())
                .build();

        advance = salaryAdvanceRepository.save(advance);
        return toAdvanceResponse(advance);
    }

    public SalaryAdvanceResponse processSalaryAdvance(UUID id, String action, UserPrincipal principal) {
        SalaryAdvance advance = salaryAdvanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Salary advance not found"));

        switch (action.toUpperCase()) {
            case "APPROVE" -> {
                advance.setStatus(SalaryAdvance.AdvanceStatus.APPROVED);
                advance.setApprovedById(principal.getId());
            }
            case "REJECT" -> advance.setStatus(SalaryAdvance.AdvanceStatus.REJECTED);
            case "DISBURSE" -> advance.setStatus(SalaryAdvance.AdvanceStatus.DISBURSED);
            default -> throw new IllegalArgumentException("Invalid action: " + action);
        }

        advance = salaryAdvanceRepository.save(advance);
        return toAdvanceResponse(advance);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Indian New Tax Regime slabs (FY 2024-25 onwards):
     * 0-3L: 0%, 3-7L: 5%, 7-10L: 10%, 10-12L: 15%, 12-15L: 20%, 15L+: 30%
     * Standard deduction: 75,000
     */
    BigDecimal calculateTDS(BigDecimal annualIncome) {
        BigDecimal standardDeduction = BigDecimal.valueOf(75000);
        BigDecimal taxableIncome = annualIncome.subtract(standardDeduction);
        if (taxableIncome.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal remaining = taxableIncome;

        // Slab boundaries and rates
        BigDecimal[] slabLimits = {
                BigDecimal.valueOf(300000),
                BigDecimal.valueOf(400000),  // 3L to 7L = 4L
                BigDecimal.valueOf(300000),  // 7L to 10L = 3L
                BigDecimal.valueOf(200000),  // 10L to 12L = 2L
                BigDecimal.valueOf(300000),  // 12L to 15L = 3L
        };
        BigDecimal[] rates = {
                BigDecimal.ZERO,                               // 0-3L: 0%
                BigDecimal.valueOf(0.05),                      // 3-7L: 5%
                BigDecimal.valueOf(0.10),                      // 7-10L: 10%
                BigDecimal.valueOf(0.15),                      // 10-12L: 15%
                BigDecimal.valueOf(0.20),                      // 12-15L: 20%
        };

        for (int i = 0; i < slabLimits.length && remaining.compareTo(BigDecimal.ZERO) > 0; i++) {
            BigDecimal slabAmount = remaining.min(slabLimits[i]);
            tax = tax.add(slabAmount.multiply(rates[i]));
            remaining = remaining.subtract(slabAmount);
        }

        // Remaining above 15L at 30%
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            tax = tax.add(remaining.multiply(BigDecimal.valueOf(0.30)));
        }

        return tax.setScale(0, RoundingMode.CEILING);
    }

    int getWorkingDaysInMonth(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        int workingDays = 0;
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            DayOfWeek dow = LocalDate.of(year, month, day).getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workingDays++;
            }
        }
        return workingDays;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private PayrollSummaryResponse buildSummary(int month, int year, List<Payslip> payslips) {
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalPf = BigDecimal.ZERO;
        BigDecimal totalEsi = BigDecimal.ZERO;

        String status = "DRAFT";
        for (Payslip p : payslips) {
            totalGross = totalGross.add(defaultZero(p.getGrossEarnings()));
            totalDeductions = totalDeductions.add(defaultZero(p.getTotalDeductions()));
            totalNet = totalNet.add(defaultZero(p.getNetSalary()));
            totalPf = totalPf.add(defaultZero(p.getPfEmployee())).add(defaultZero(p.getPfEmployer()));
            totalEsi = totalEsi.add(defaultZero(p.getEsiEmployee())).add(defaultZero(p.getEsiEmployer()));
            status = p.getStatus().name();
        }

        return new PayrollSummaryResponse(
                month, year, payslips.size(),
                totalGross, totalDeductions, totalNet,
                totalPf, totalEsi, status
        );
    }

    private PayslipResponse toPayslipResponse(Payslip p) {
        Employee emp = p.getEmployee();
        String empName = emp.getFirstName() + " " + emp.getLastName();
        String deptName = emp.getDepartment() != null ? emp.getDepartment().getName() : null;

        return new PayslipResponse(
                p.getId(),
                emp.getId(),
                empName,
                emp.getEmployeeId(),
                deptName,
                p.getMonth(),
                p.getYear(),
                p.getBasicSalary(),
                p.getHra(),
                p.getConveyanceAllowance(),
                p.getMedicalAllowance(),
                p.getSpecialAllowance(),
                p.getOtherEarnings(),
                p.getGrossEarnings(),
                p.getPfEmployee(),
                p.getEsiEmployee(),
                p.getProfessionalTax(),
                p.getTds(),
                p.getLopDeduction(),
                p.getOtherDeductions(),
                p.getTotalDeductions(),
                p.getNetSalary(),
                p.getLopDays(),
                p.getWorkingDays(),
                p.getPresentDays(),
                p.getPaidDays(),
                p.getPfEmployer(),
                p.getEsiEmployer(),
                p.getStatus().name(),
                p.getProcessedDate(),
                p.getApprovedById(),
                p.getPaidDate(),
                p.getTransactionReference()
        );
    }

    private SalaryAdvanceResponse toAdvanceResponse(SalaryAdvance a) {
        Employee emp = a.getEmployee();
        return new SalaryAdvanceResponse(
                a.getId(),
                emp.getFirstName() + " " + emp.getLastName(),
                a.getAmount(),
                a.getRequestDate(),
                a.getStatus().name(),
                a.getEmiMonths(),
                a.getEmiAmount(),
                a.getRemainingAmount()
        );
    }
}
