package com.vikisol.one.payroll.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.payroll.dto.*;
import com.vikisol.one.payroll.entity.PayrollConfig;
import com.vikisol.one.payroll.service.PayrollService;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    // ── Payroll Operations ──────────────────────────────────────────────────

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'FINANCE', 'CEO')")
    public ResponseEntity<ApiResponse<PayrollSummaryResponse>> runPayroll(
            @Valid @RequestBody PayrollRunRequest request) {
        PayrollSummaryResponse summary = payrollService.runPayroll(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payroll processed successfully", summary));
    }

    @PutMapping("/approve")
    @PreAuthorize("hasAnyRole('CEO', 'FINANCE')")
    public ResponseEntity<ApiResponse<Void>> approvePayroll(
            @RequestParam int month,
            @RequestParam int year,
            @AuthenticationPrincipal UserPrincipal principal) {
        payrollService.approvePayroll(month, year, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payroll approved successfully", null));
    }

    @PutMapping("/mark-paid")
    @PreAuthorize("hasAnyRole('FINANCE', 'CEO')")
    public ResponseEntity<ApiResponse<Void>> markAsPaid(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam String ref) {
        payrollService.markAsPaid(month, year, ref);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payroll marked as paid", null));
    }

    // ── Payslip Queries ─────────────────────────────────────────────────────

    @GetMapping("/payslip/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'FINANCE', 'CEO', 'ADMIN')")
    public ResponseEntity<ApiResponse<PayslipResponse>> getPayslip(
            @PathVariable UUID employeeId,
            @RequestParam int month,
            @RequestParam int year) {
        PayslipResponse payslip = payrollService.getPayslip(employeeId, month, year);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payslip retrieved", payslip));
    }

    // HR/Finance/CEO/Admin view of every employee's payslips - the admin Payroll page previously
    // had no real "all payslips" endpoint and was unintentionally using /my-payslips, so it only
    // ever showed the logged-in user's own records.
    @GetMapping("/payslips")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'FINANCE', 'CEO', 'ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<PayslipResponse>>> getAllPayslips(Pageable pageable) {
        PagedResponse<PayslipResponse> payslips = payrollService.getAllPayslips(pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payslips retrieved", payslips));
    }

    @GetMapping("/my-payslips")
    public ResponseEntity<ApiResponse<PagedResponse<PayslipResponse>>> getMyPayslips(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        PagedResponse<PayslipResponse> payslips = payrollService.getMyPayslips(principal, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payslips retrieved", payslips));
    }

    @PostMapping("/payslip/{payslipId}/generate-pdf")
    public ResponseEntity<ApiResponse<String>> generatePayslipPdf(
            @PathVariable UUID payslipId, @AuthenticationPrincipal UserPrincipal principal) {
        String fileUrl = payrollService.generatePayslipPdf(payslipId, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payslip PDF generated", fileUrl));
    }

    @PostMapping("/payslips/bulk-download")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'FINANCE', 'CEO', 'ADMIN')")
    public ResponseEntity<byte[]> bulkDownloadPayslips(
            @RequestBody List<UUID> payslipIds, @AuthenticationPrincipal UserPrincipal principal) {
        byte[] zip = payrollService.bulkDownloadPayslipsZip(payslipIds, principal);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Payslips.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }

    @PostMapping("/payslips/bulk-email")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'FINANCE', 'CEO', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> bulkEmailPayslips(
            @RequestBody List<UUID> payslipIds, @AuthenticationPrincipal UserPrincipal principal) {
        payrollService.bulkEmailPayslips(payslipIds, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payslips emailed", null));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'FINANCE', 'CEO')")
    public ResponseEntity<ApiResponse<PayrollSummaryResponse>> getPayrollSummary(
            @RequestParam int month,
            @RequestParam int year) {
        PayrollSummaryResponse summary = payrollService.getPayrollSummary(month, year);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payroll summary retrieved", summary));
    }

    // ── Config ──────────────────────────────────────────────────────────────

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('FINANCE', 'HR_MANAGER', 'CEO')")
    public ResponseEntity<ApiResponse<List<PayrollConfig>>> getAllConfigs() {
        List<PayrollConfig> configs = payrollService.getAllConfigs();
        return ResponseEntity.ok(new ApiResponse<>(true, "Configs retrieved", configs));
    }

    @PutMapping("/config")
    @PreAuthorize("hasAnyRole('FINANCE', 'HR_MANAGER', 'CEO')")
    public ResponseEntity<ApiResponse<PayrollConfig>> updateConfig(
            @Valid @RequestBody PayrollConfigRequest request) {
        PayrollConfig config = payrollService.updateConfig(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Config updated", config));
    }

    // ── CTC Breakup Template (CEO sets it once, applies to all offers/employees) ──

    @GetMapping("/ctc-breakup-template")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'CEO', 'RECRUITER', 'ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, java.math.BigDecimal>>> getCtcBreakupTemplate() {
        return ResponseEntity.ok(new ApiResponse<>(true, "CTC breakup template retrieved", payrollService.getCtcBreakupTemplate()));
    }

    @PutMapping("/ctc-breakup-template")
    @PreAuthorize("hasRole('CEO')")
    public ResponseEntity<ApiResponse<java.util.Map<String, java.math.BigDecimal>>> updateCtcBreakupTemplate(
            @RequestBody java.util.Map<String, java.math.BigDecimal> percentages) {
        return ResponseEntity.ok(new ApiResponse<>(true, "CTC breakup template updated", payrollService.updateCtcBreakupTemplate(percentages)));
    }

    @PostMapping("/ctc-breakup-preview")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'CEO', 'RECRUITER', 'ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, java.math.BigDecimal>>> previewCtcBreakup(
            @RequestParam java.math.BigDecimal ctc) {
        return ResponseEntity.ok(new ApiResponse<>(true, "CTC breakup computed", payrollService.computeCtcBreakup(ctc)));
    }

    // CEO-chosen name for the 6th, custom CTC component (e.g. "LTA", "Bonus"). 0% until named/set.
    @GetMapping("/ctc-custom-label")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'CEO', 'RECRUITER', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> getCtcCustomLabel() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Custom CTC label retrieved", payrollService.getCtcCustomLabel()));
    }

    @PutMapping("/ctc-custom-label")
    @PreAuthorize("hasRole('CEO')")
    public ResponseEntity<ApiResponse<String>> updateCtcCustomLabel(@RequestBody java.util.Map<String, String> body) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Custom CTC label updated", payrollService.updateCtcCustomLabel(body.get("label"))));
    }

    // ── Salary Advance ──────────────────────────────────────────────────────

    @PostMapping("/salary-advance")
    public ResponseEntity<ApiResponse<SalaryAdvanceResponse>> requestSalaryAdvance(
            @Valid @RequestBody SalaryAdvanceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        SalaryAdvanceResponse response = payrollService.requestSalaryAdvance(request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Salary advance requested", response));
    }

    @PutMapping("/salary-advance/{id}/action")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'FINANCE', 'CEO')")
    public ResponseEntity<ApiResponse<SalaryAdvanceResponse>> processSalaryAdvance(
            @PathVariable UUID id,
            @RequestParam String action,
            @AuthenticationPrincipal UserPrincipal principal) {
        SalaryAdvanceResponse response = payrollService.processSalaryAdvance(id, action, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Salary advance " + action.toLowerCase() + "d", response));
    }
}
