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

    @GetMapping("/my-payslips")
    public ResponseEntity<ApiResponse<PagedResponse<PayslipResponse>>> getMyPayslips(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        PagedResponse<PayslipResponse> payslips = payrollService.getMyPayslips(principal, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payslips retrieved", payslips));
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
