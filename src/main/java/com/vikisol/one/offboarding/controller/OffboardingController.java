package com.vikisol.one.offboarding.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.offboarding.dto.*;
import com.vikisol.one.offboarding.entity.OffboardingCase;
import com.vikisol.one.offboarding.entity.OffboardingChecklistItem;
import com.vikisol.one.offboarding.repository.OffboardingChecklistItemRepository;
import com.vikisol.one.offboarding.service.OffboardingService;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Offboarding lifecycle management - mirrors BackgroundCheckController's role-gating style:
// CEO/HR_MANAGER/ADMIN manage everything, the employee's reporting manager can view + act on
// their own reports' cases, and the employee themselves gets read-only visibility into their own case.
@RestController
@RequiredArgsConstructor
public class OffboardingController {

    private final OffboardingService offboardingService;
    private final EmployeeRepository employeeRepository;
    private final OffboardingChecklistItemRepository checklistItemRepository;

    private boolean isPrivileged(UserPrincipal principal) {
        return principal.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isManagerOf(UserPrincipal principal, Employee employee) {
        return employeeRepository.findByUserId(principal.getId())
                .map(managerRecord -> managerRecord.getId().equals(employee.getReportingManagerId()))
                .orElse(false);
    }

    private boolean isSelf(UserPrincipal principal, Employee employee) {
        return employee.getUser() != null && employee.getUser().getId().equals(principal.getId());
    }

    private void assertCanView(UserPrincipal principal, Employee employee) {
        if (!isPrivileged(principal) && !isManagerOf(principal, employee) && !isSelf(principal, employee)) {
            throw new BadRequestException("You do not have permission to view this employee's offboarding status");
        }
    }

    private void assertCanAct(UserPrincipal principal, Employee employee) {
        if (!isPrivileged(principal) && !isManagerOf(principal, employee)) {
            throw new BadRequestException("You do not have permission to manage this employee's offboarding case");
        }
    }

    private UUID actorEmployeeId(UserPrincipal principal) {
        return employeeRepository.findByUserId(principal.getId()).map(Employee::getId).orElse(null);
    }

    // --- Per-employee endpoints ---

    @PostMapping("/employees/{employeeId}/offboarding/initiate")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<OffboardingCaseResponse>> initiate(
            @PathVariable UUID employeeId, @RequestBody InitiateOffboardingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Offboarding initiated",
                offboardingService.initiateOffboarding(employeeId, request, actorEmployeeId(principal))));
    }

    @GetMapping("/employees/{employeeId}/offboarding")
    public ResponseEntity<ApiResponse<OffboardingCaseResponse>> getForEmployee(
            @PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        assertCanView(principal, employee);
        return ResponseEntity.ok(new ApiResponse<>(true, "Offboarding case retrieved",
                offboardingService.getCaseByEmployee(employeeId)));
    }

    @GetMapping("/employees/{employeeId}/offboarding/exit-package")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ByteArrayResource> downloadExitPackage(@PathVariable UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        byte[] zipBytes = offboardingService.buildExitPackageZip(employeeId);
        String fileName = "ExitPackage_" + employee.getEmployeeId() + ".zip";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(new ByteArrayResource(zipBytes));
    }

    @GetMapping("/employees/{employeeId}/offboarding/exit-package/merged-pdf")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ByteArrayResource> downloadExitPackageMergedPdf(@PathVariable UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        byte[] pdfBytes = offboardingService.buildExitPackageMergedPdf(employeeId);
        String fileName = "ExitPackage_" + employee.getEmployeeId() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(new ByteArrayResource(pdfBytes));
    }

    @PostMapping("/employees/{employeeId}/offboarding/exit-package/email")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> emailExitPackage(
            @PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        offboardingService.emailExitPackage(employeeId, actorEmployeeId(principal));
        return ResponseEntity.ok(new ApiResponse<>(true, "Exit package emailed to employee", null));
    }

    // --- Case-level endpoints ---

    @GetMapping("/offboarding/{caseId}")
    public ResponseEntity<ApiResponse<OffboardingCaseResponse>> getCase(
            @PathVariable UUID caseId, @AuthenticationPrincipal UserPrincipal principal) {
        OffboardingCaseResponse response = offboardingService.getCase(caseId);
        Employee employee = employeeRepository.findById(response.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        assertCanView(principal, employee);
        return ResponseEntity.ok(new ApiResponse<>(true, "Offboarding case retrieved", response));
    }

    @PostMapping("/offboarding/{caseId}/advance-stage")
    public ResponseEntity<ApiResponse<OffboardingCaseResponse>> advanceStage(
            @PathVariable UUID caseId, @RequestBody AdvanceStageRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        OffboardingCaseResponse existing = offboardingService.getCase(caseId);
        Employee employee = employeeRepository.findById(existing.employeeId())
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
        assertCanAct(principal, employee);
        return ResponseEntity.ok(new ApiResponse<>(true, "Stage advanced",
                offboardingService.advanceStage(caseId, request, actorEmployeeId(principal))));
    }

    @PostMapping("/offboarding/{caseId}/cancel")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<OffboardingCaseResponse>> cancel(
            @PathVariable UUID caseId, @RequestParam(required = false) String comments,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Offboarding case cancelled",
                offboardingService.cancelCase(caseId, actorEmployeeId(principal), comments)));
    }

    @PutMapping("/offboarding/checklist/{itemId}")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> updateChecklistItem(
            @PathVariable UUID itemId, @RequestBody ChecklistUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        // Any privileged or manager-of-the-employee user may update; self-service by the exiting
        // employee is intentionally not allowed (checklist completion must be attested by HR/IT/
        // Finance/manager, not self-certified). The previous check only verified the caller had
        // SOME employee record linked - it never resolved which employee this specific checklist
        // item belongs to, so any authenticated employee could act on any other employee's exit
        // checklist. Now resolves the real target employee and reuses assertCanAct.
        OffboardingChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new EntityNotFoundException("Checklist item not found"));
        Employee targetEmployee = item.getOffboardingCase().getEmployee();
        assertCanAct(principal, targetEmployee);
        return ResponseEntity.ok(new ApiResponse<>(true, "Checklist item updated",
                offboardingService.updateChecklistItem(itemId, request, actorEmployeeId(principal))));
    }

    // --- List / dashboard endpoints ---

    @GetMapping("/offboarding")
    public ResponseEntity<ApiResponse<List<OffboardingCaseSummary>>> listCases(
            @RequestParam(required = false) OffboardingCase.CaseStatus status,
            @RequestParam(required = false) OffboardingCase.Stage stage,
            @RequestParam(required = false) UUID departmentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID managerFilter = null;
        if (!isPrivileged(principal)) {
            managerFilter = employeeRepository.findByUserId(principal.getId()).map(Employee::getId)
                    .orElseThrow(() -> new BadRequestException("You do not have permission to view offboarding cases"));
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Offboarding cases retrieved",
                offboardingService.listCases(status, stage, departmentId, managerFilter)));
    }

    @GetMapping("/offboarding/dashboard-stats")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<OffboardingDashboardStats>> dashboardStats() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Offboarding dashboard stats retrieved",
                offboardingService.getDashboardStats()));
    }
}
