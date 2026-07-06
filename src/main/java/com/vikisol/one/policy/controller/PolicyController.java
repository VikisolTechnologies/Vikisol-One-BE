package com.vikisol.one.policy.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.policy.dto.*;
import com.vikisol.one.policy.service.PolicyAcknowledgementService;
import com.vikisol.one.policy.service.PolicyService;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Company Policies - every authenticated role can list/read/view/accept policies (everyone needs
// to see and acknowledge them); creating/editing/disabling and the compliance report are
// CEO/HR Manager/Admin only.
@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;
    private final PolicyAcknowledgementService acknowledgementService;
    private final EmployeeRepository employeeRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PolicyResponse>>> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean canSeeInactive = includeInactive && hasManagementRole(principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Policies retrieved", policyService.list(canSeeInactive)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PolicyResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Policy retrieved", policyService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PolicyResponse>> create(@RequestBody PolicyRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Policy created", policyService.create(request, principal.getEmail())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<PolicyResponse>> update(@PathVariable UUID id, @RequestBody PolicyRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Policy updated", policyService.update(id, request)));
    }

    // Soft-disable (active=false), not a hard delete - see PolicyService.disable for rationale.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> disable(@PathVariable UUID id) {
        policyService.disable(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Policy disabled", null));
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<ApiResponse<Void>> recordView(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        acknowledgementService.recordView(id, currentEmployeeId(principal));
        return ResponseEntity.ok(new ApiResponse<>(true, "View recorded", null));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<AcknowledgementStatusResponse>> recordAcceptance(
            @PathVariable UUID id, @RequestBody AcceptPolicyRequest request,
            @AuthenticationPrincipal UserPrincipal principal, HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = httpRequest.getRemoteAddr();
        AcknowledgementStatusResponse status = acknowledgementService.recordAcceptance(
                id, currentEmployeeId(principal), request.signatureText(), ip);
        return ResponseEntity.ok(new ApiResponse<>(true, "Policy accepted", status));
    }

    @GetMapping("/{id}/acknowledgement-status")
    public ResponseEntity<ApiResponse<AcknowledgementStatusResponse>> getStatus(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Status retrieved", acknowledgementService.getStatus(id, currentEmployeeId(principal))));
    }

    @GetMapping("/{id}/acknowledgements")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<PolicyAcknowledgementRecord>>> listAcknowledgements(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Acknowledgements retrieved", acknowledgementService.listForPolicy(id)));
    }

    private boolean hasManagementRole(UserPrincipal principal) {
        return principal.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private UUID currentEmployeeId(UserPrincipal principal) {
        return employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("No employee record linked to this account"))
                .getId();
    }
}
