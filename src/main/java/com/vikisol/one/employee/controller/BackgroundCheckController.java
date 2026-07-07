package com.vikisol.one.employee.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.employee.dto.BackgroundCheckResponse;
import com.vikisol.one.employee.dto.BackgroundCheckUpdateRequest;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.employee.service.BackgroundCheckService;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/employees/{employeeId}/bgv")
@RequiredArgsConstructor
public class BackgroundCheckController {

    private final BackgroundCheckService backgroundCheckService;
    private final EmployeeRepository employeeRepository;

    // Employee can see their own BGV status (transparency into where their verification stands);
    // HR/CEO/Admin can see anyone's, for the review workflow.
    @GetMapping
    public ResponseEntity<ApiResponse<List<BackgroundCheckResponse>>> getForEmployee(
            @PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        boolean privileged = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECRUITER"));
        boolean isSelf = employeeRepository.findById(employeeId)
                .map(e -> e.getUser() != null && e.getUser().getId().equals(principal.getId()))
                .orElse(false);
        if (!privileged && !isSelf) throw new BadRequestException("You do not have permission to view this employee's background verification status");
        return ResponseEntity.ok(new ApiResponse<>(true, "Background verification retrieved", backgroundCheckService.getForEmployee(employeeId)));
    }

    // Only HR/CEO/Admin can move a check through its review workflow - this is a compliance
    // record, not something an employee, their manager, or the sourcing recruiter can self-certify.
    @PutMapping("/{checkId}")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<BackgroundCheckResponse>> updateStatus(
            @PathVariable UUID employeeId, @PathVariable UUID checkId,
            @RequestBody BackgroundCheckUpdateRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        UUID reviewerEmployeeId = employeeRepository.findByUserId(principal.getId()).map(e -> e.getId()).orElse(null);
        return ResponseEntity.ok(new ApiResponse<>(true, "Background check updated", backgroundCheckService.updateStatus(checkId, request, reviewerEmployeeId)));
    }

    public record RemarksRequest(String remarks) {}

    // Recruiter can comment (add remarks) without touching status - distinct, narrower permission
    // from updateStatus above, which stays HR/CEO/Admin-only.
    @PutMapping("/{checkId}/remarks")
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN','RECRUITER')")
    public ResponseEntity<ApiResponse<BackgroundCheckResponse>> addRemarks(
            @PathVariable UUID employeeId, @PathVariable UUID checkId, @RequestBody RemarksRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Remarks added", backgroundCheckService.addRemarks(checkId, request.remarks())));
    }

    public record AttachDocumentRequest(UUID documentId) {}

    // Employee (their own check), Recruiter, HR, CEO, Admin can all attach supporting documents -
    // uploading evidence isn't the same privilege as approving/rejecting a check.
    @PutMapping("/{checkId}/document")
    public ResponseEntity<ApiResponse<BackgroundCheckResponse>> attachDocument(
            @PathVariable UUID employeeId, @PathVariable UUID checkId,
            @RequestBody AttachDocumentRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        boolean privileged = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RECRUITER"));
        boolean isSelf = employeeRepository.findById(employeeId)
                .map(e -> e.getUser() != null && e.getUser().getId().equals(principal.getId()))
                .orElse(false);
        if (!privileged && !isSelf) throw new BadRequestException("You do not have permission to attach documents to this employee's background verification");
        return ResponseEntity.ok(new ApiResponse<>(true, "Document attached", backgroundCheckService.attachDocument(checkId, request.documentId())));
    }
}
