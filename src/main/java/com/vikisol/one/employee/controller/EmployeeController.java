package com.vikisol.one.employee.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.employee.dto.EmployeeListResponse;
import com.vikisol.one.employee.dto.EmployeeRequest;
import com.vikisol.one.employee.dto.EmployeeResponse;
import com.vikisol.one.employee.dto.HikeRequest;
import com.vikisol.one.employee.dto.OnboardingChecklistRequest;
import com.vikisol.one.employee.dto.ResignationRequest;
import com.vikisol.one.employee.service.EmployeeService;
import com.vikisol.one.security.RoleEnum;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeListResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<EmployeeListResponse> employees = employeeService.getAll(pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employees retrieved", employees));
    }

    // Any authenticated role can call this (directory browsing needs it), but the service masks
    // sensitive PII (bank details, PAN, Aadhar, salary) unless the caller is CEO/HR/Admin or
    // viewing their own record - this endpoint had no such restriction before.
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getById(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        EmployeeResponse employee = employeeService.getById(id, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee retrieved", employee));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<EmployeeResponse>> getProfile(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        EmployeeResponse employee = employeeService.getProfile(userPrincipal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile retrieved", employee));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> create(@RequestBody EmployeeRequest request) {
        EmployeeResponse employee = employeeService.createEmployee(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee created", employee));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> update(@PathVariable UUID id, @RequestBody EmployeeRequest request) {
        EmployeeResponse employee = employeeService.updateEmployee(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee updated", employee));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        employeeService.deactivate(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee deactivated", null));
    }

    @GetMapping("/department/{deptId}")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeListResponse>>> getByDepartment(
            @PathVariable UUID deptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<EmployeeListResponse> employees = employeeService.getByDepartment(deptId, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Employees retrieved", employees));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<EmployeeListResponse>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<EmployeeListResponse> employees = employeeService.search(q, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Search results", employees));
    }

    // Lightweight manager list for dropdowns (e.g. recruiter selecting a reporting manager for an offer).
    // Open to any authenticated role - unlike the full employee list, this doesn't expose sensitive data.
    @GetMapping("/managers")
    public ResponseEntity<ApiResponse<List<com.vikisol.one.employee.dto.ManagerOptionResponse>>> getManagerOptions() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Manager options retrieved", employeeService.getManagerOptions()));
    }

    @GetMapping("/reporting/{managerId}")
    public ResponseEntity<ApiResponse<List<EmployeeListResponse>>> getByReportingManager(@PathVariable UUID managerId) {
        List<EmployeeListResponse> employees = employeeService.getByReportingManager(managerId);
        return ResponseEntity.ok(new ApiResponse<>(true, "Direct reports retrieved", employees));
    }

    // Revises CTC using the CEO's standard breakup template and emails a hike letter.
    @PostMapping("/{id}/hike")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> issueHike(@PathVariable UUID id, @RequestBody HikeRequest request) {
        EmployeeResponse employee = employeeService.issueHike(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Hike issued and letter emailed", employee));
    }

    // Records a resignation and emails an acknowledgement letter.
    @PostMapping("/{id}/resign")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> recordResignation(@PathVariable UUID id, @RequestBody ResignationRequest request) {
        EmployeeResponse employee = employeeService.recordResignation(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Resignation recorded and acknowledgement emailed", employee));
    }

    // Promotes/changes an employee's application login role (e.g. EMPLOYEE -> MANAGER). CEO only.
    @PutMapping("/{id}/account-role")
    @PreAuthorize("hasRole('CEO')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> changeAccountRole(@PathVariable UUID id, @RequestParam RoleEnum role) {
        EmployeeResponse employee = employeeService.changeAccountRole(id, role);
        return ResponseEntity.ok(new ApiResponse<>(true, "Account role updated", employee));
    }

    // Generates (or regenerates) an existing employee's offer letter PDF from their current record.
    @PostMapping("/{id}/generate-offer-letter")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> generateOfferLetter(@PathVariable UUID id) {
        String fileUrl = employeeService.generateOfferLetter(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Offer letter generated", fileUrl));
    }

    // Resets an employee's login password to a new temp password and emails it to them.
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable UUID id) {
        employeeService.resetPassword(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Password reset and emailed to the employee", null));
    }

    // Updates the onboarding checklist (documents verified, assets assigned, bank details, induction).
    @PutMapping("/{id}/onboarding")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateOnboarding(@PathVariable UUID id, @RequestBody OnboardingChecklistRequest request) {
        EmployeeResponse employee = employeeService.updateOnboardingChecklist(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Onboarding checklist updated", employee));
    }
}
