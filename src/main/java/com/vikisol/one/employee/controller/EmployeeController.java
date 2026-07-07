package com.vikisol.one.employee.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.employee.dto.EmployeeListResponse;
import com.vikisol.one.employee.dto.EmployeeRequest;
import com.vikisol.one.employee.dto.EmployeeResponse;
import com.vikisol.one.employee.dto.HikeRequest;
import com.vikisol.one.employee.dto.OnboardingChecklistRequest;
import com.vikisol.one.employee.dto.ResignationRequest;
import com.vikisol.one.employee.service.EmployeeDashboardService;
import com.vikisol.one.employee.service.EmployeeService;
import com.vikisol.one.employee.service.EmployeeTimelineService;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.common.exception.BadRequestException;
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
    private final EmployeeTimelineService employeeTimelineService;
    private final EmployeeDashboardService employeeDashboardService;
    private final com.vikisol.one.employee.service.AccountStatusService accountStatusService;
    private final com.vikisol.one.auth.service.LoginLockoutService loginLockoutService;
    private final EmployeeRepository employeeRepository;

    // Real-time inline validation for the Add/Edit Employee form - lets HR see "already exists"
    // beside the field before submitting, instead of a raw DB constraint violation surfacing only
    // on save. Any/all params may be omitted; only the ones present are checked. Registered before
    // the plain "/employees" mapping so Spring doesn't need path-variable disambiguation.
    @GetMapping("/validate")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<com.vikisol.one.employee.dto.EmployeeFieldValidationResponse>> validateFields(
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String officialEmail,
            @RequestParam(required = false) String personalEmail,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String pan,
            @RequestParam(required = false) String aadhaar,
            @RequestParam(required = false) String pf,
            @RequestParam(required = false) String uan) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Validation checked",
                employeeService.validateFields(employeeId, officialEmail, personalEmail, mobile, pan, aadhaar, pf, uan)));
    }

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

    // Self-service update for the Onboarding Wizard's Personal/Bank/Tax/Nominee steps - the plain
    // admin PUT above is CEO/HR/Admin-only, which previously made every employee's own onboarding
    // Save action 403 with no working alternative. Only self-editable fields are ever applied
    // server-side (see EmployeeService.updateOwnProfile) regardless of what else is in the body.
    @PutMapping("/{id}/personal-profile")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateOwnProfile(
            @PathVariable UUID id, @RequestBody EmployeeRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        boolean privileged = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
        boolean isSelf = employeeRepository.findById(id)
                .map(e -> e.getUser() != null && e.getUser().getId().equals(principal.getId()))
                .orElse(false);
        if (!privileged && !isSelf) throw new BadRequestException("You do not have permission to update this employee's profile");
        return ResponseEntity.ok(new ApiResponse<>(true, "Profile updated", employeeService.updateOwnProfile(id, request)));
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

    @PostMapping("/{id}/generate-experience-letter")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> generateExperienceLetter(@PathVariable UUID id) {
        String fileUrl = employeeService.generateExperienceLetter(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Experience letter generated", fileUrl));
    }

    @PostMapping("/{id}/generate-relieving-letter")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> generateRelievingLetter(@PathVariable UUID id) {
        String fileUrl = employeeService.generateRelievingLetter(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Relieving letter generated", fileUrl));
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

    // Manual HR override for lifecycle transitions nothing else drives automatically (e.g. PROBATION -> CONFIRMED).
    @PutMapping("/{id}/lifecycle-status")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<EmployeeResponse>> updateLifecycleStatus(
            @PathVariable UUID id, @RequestBody com.vikisol.one.employee.dto.LifecycleStatusRequest request) {
        EmployeeResponse employee = employeeService.updateLifecycleStatus(id, request.status());
        return ResponseEntity.ok(new ApiResponse<>(true, "Lifecycle status updated", employee));
    }

    @GetMapping("/{employeeId}/timeline")
    public ResponseEntity<ApiResponse<List<com.vikisol.one.employee.dto.EmployeeTimelineEntry>>> getTimeline(@PathVariable UUID employeeId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Employee timeline retrieved", employeeTimelineService.getTimeline(employeeId)));
    }

    // Self-or-privileged, matching BackgroundCheckController's pattern - this is the employee's own
    // "Home" dashboard, but HR/CEO/Admin/a direct manager may also need to preview it.
    @GetMapping("/{employeeId}/dashboard-summary")
    public ResponseEntity<ApiResponse<com.vikisol.one.employee.dto.EmployeeDashboardSummaryResponse>> getDashboardSummary(
            @PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        boolean privileged = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
        boolean isSelf = employeeRepository.findById(employeeId)
                .map(e -> e.getUser() != null && e.getUser().getId().equals(principal.getId()))
                .orElse(false);
        if (!privileged && !isSelf) throw new BadRequestException("You do not have permission to view this employee's dashboard");
        return ResponseEntity.ok(new ApiResponse<>(true, "Dashboard summary retrieved", employeeDashboardService.getSummary(employeeId)));
    }

    // Self-or-privileged, same pattern as dashboard-summary above - backs the Employee Profile's
    // "Linked Accounts" panel.
    @GetMapping("/{employeeId}/account-status")
    public ResponseEntity<ApiResponse<com.vikisol.one.employee.dto.AccountStatusResponse>> getAccountStatus(
            @PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        boolean privileged = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
        boolean isSelf = employeeRepository.findById(employeeId)
                .map(e -> e.getUser() != null && e.getUser().getId().equals(principal.getId()))
                .orElse(false);
        if (!privileged && !isSelf) throw new BadRequestException("You do not have permission to view this employee's account status");
        return ResponseEntity.ok(new ApiResponse<>(true, "Account status retrieved", accountStatusService.getAccountStatus(employeeId)));
    }

    // Manual unlock (Security Center / Linked Accounts panel) - lets an admin clear a lockout
    // before it expires on its own, rather than waiting out the configured lockout duration.
    @PostMapping("/{employeeId}/unlock-account")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unlockAccount(@PathVariable UUID employeeId) {
        var employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BadRequestException("Employee not found"));
        if (employee.getUser() == null) throw new BadRequestException("This employee has no linked login account");
        loginLockoutService.unlockAccount(employee.getUser().getEmail());
        return ResponseEntity.ok(new ApiResponse<>(true, "Account unlocked", null));
    }
}
