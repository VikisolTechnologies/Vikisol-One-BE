package com.vikisol.one.doctemplate.service;

import com.vikisol.one.settings.dto.BrandingDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

// Shared {{Placeholder}} field-building logic for the Corporate Offer Letter template, used by
// both RecruitmentService.approveSelection() (new hire, from Candidate data) and
// EmployeeService.generateOfferLetter() (regenerate for an existing Employee record) - kept in
// one place so the two callers can't drift out of sync on field names/formatting, and so
// company-wide values (office location, work hours, orientation contact) come from
// BrandingDto/Company Branding settings instead of being hardcoded in either caller.
public final class OfferLetterFieldsHelper {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final int ANNUAL_MONTHS = 12;

    private OfferLetterFieldsHelper() {
    }

    /**
     * @param salutation "Mr."/"Ms." if derivable (e.g. from Employee.gender), otherwise a
     *                   sensible default ("Mr./Ms.") - Candidate has no gender field on file today
     *                   so the recruitment-time offer letter cannot derive this reliably.
     */
    public static Map<String, String> build(String employeeName, String designationTitle, String salutation,
                                             LocalDate joiningDate, String reportingManagerTitle, String reportingManagerName,
                                             Map<String, BigDecimal> breakup, BigDecimal totalCtcAnnual, BigDecimal professionalTax,
                                             BrandingDto branding) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("EmployeeName", employeeName);
        fields.put("Designation", designationTitle != null ? designationTitle : "");
        fields.put("OfferDate", LocalDate.now().format(DISPLAY_DATE));
        fields.put("Salutation", salutation != null ? salutation : "Mr./Ms.");
        fields.put("MonthlySalary", breakup.getOrDefault("grossSalary", BigDecimal.ZERO).toPlainString());
        fields.put("Location", branding.officeLocation());
        fields.put("ReportingManagerTitle", reportingManagerTitle != null ? reportingManagerTitle : "Reporting Manager");
        fields.put("ReportingManagerName", reportingManagerName != null ? reportingManagerName : "");
        fields.put("WorkStartTime", branding.workStartTime());
        fields.put("WorkEndTime", branding.workEndTime());
        fields.put("WorkDays", branding.workDays());
        fields.put("JoiningDate", joiningDate != null ? joiningDate.format(DISPLAY_DATE) : "");
        fields.put("JoiningTime", branding.workStartTime());
        fields.put("OrientationContact", branding.orientationContact());
        // Acceptance deadline is intentionally a computed offset (joining date minus 7 days),
        // not a configurable value - there's no "policy" to move to settings here.
        fields.put("AcceptanceDeadline", joiningDate != null ? joiningDate.minusDays(7).format(DISPLAY_DATE) : "");

        BigDecimal basic = breakup.getOrDefault("basicSalary", BigDecimal.ZERO);
        BigDecimal hra = breakup.getOrDefault("hra", BigDecimal.ZERO);
        BigDecimal special = breakup.getOrDefault("specialAllowance", BigDecimal.ZERO);
        BigDecimal other = breakup.getOrDefault("customAllowance", BigDecimal.ZERO);
        BigDecimal totalFixedMonthly = breakup.getOrDefault("grossSalary", BigDecimal.ZERO);
        putMonthlyAndAnnual(fields, "Basic", basic);
        putMonthlyAndAnnual(fields, "HRA", hra);
        putMonthlyAndAnnual(fields, "SpecialAllowance", special);
        putMonthlyAndAnnual(fields, "OtherAllowance", other);
        putMonthlyAndAnnual(fields, "TotalFixed", totalFixedMonthly);
        fields.put("PTMonthly", professionalTax.toPlainString());
        fields.put("PTAnnual", professionalTax.multiply(BigDecimal.valueOf(ANNUAL_MONTHS)).toPlainString());
        fields.put("TotalCtcMonthly", totalCtcAnnual.divide(BigDecimal.valueOf(ANNUAL_MONTHS), 2, RoundingMode.HALF_UP).toPlainString());
        fields.put("TotalCtcAnnual", totalCtcAnnual.toPlainString());
        return fields;
    }

    private static void putMonthlyAndAnnual(Map<String, String> fields, String prefix, BigDecimal monthlyValue) {
        fields.put(prefix + "Monthly", monthlyValue.toPlainString());
        fields.put(prefix + "Annual", monthlyValue.multiply(BigDecimal.valueOf(ANNUAL_MONTHS)).toPlainString());
    }
}
