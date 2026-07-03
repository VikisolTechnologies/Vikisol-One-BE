package com.vikisol.one.leave.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.leave.dto.*;
import com.vikisol.one.leave.service.LeaveService;
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
@RequestMapping("/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping("/types")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN', 'CEO')")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> createLeaveType(@Valid @RequestBody LeaveTypeRequest request) {
        LeaveTypeResponse response = leaveService.createLeaveType(request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Leave type created successfully", response));
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<LeaveTypeResponse>>> getAllLeaveTypes() {
        List<LeaveTypeResponse> types = leaveService.getAllLeaveTypes();
        return ResponseEntity.ok(new ApiResponse<>(true, "Leave types retrieved successfully", types));
    }

    // CEO-configurable: adjust the annual quota (e.g. Casual/Earned/Sick/Comp days) for a leave type.
    @PutMapping("/types/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN', 'CEO')")
    public ResponseEntity<ApiResponse<LeaveTypeResponse>> updateLeaveType(
            @PathVariable UUID id, @Valid @RequestBody LeaveTypeRequest request) {
        LeaveTypeResponse response = leaveService.updateLeaveType(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Leave type updated successfully", response));
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN', 'CEO')")
    public ResponseEntity<ApiResponse<Void>> deleteLeaveType(@PathVariable UUID id) {
        leaveService.deleteLeaveType(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Leave type deleted successfully", null));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> applyLeave(
            @Valid @RequestBody LeaveApplyRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LeaveRequestResponse response = leaveService.applyLeave(request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Leave applied successfully", response));
    }

    @PutMapping("/{id}/action")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR_MANAGER', 'CEO')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> processLeaveAction(
            @PathVariable UUID id,
            @Valid @RequestBody LeaveActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        LeaveRequestResponse response = leaveService.processLeaveAction(id, request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Leave request processed successfully", response));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelLeave(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        leaveService.cancelLeave(id, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Leave cancelled successfully", null));
    }

    @GetMapping("/my-requests")
    public ResponseEntity<ApiResponse<PagedResponse<LeaveRequestResponse>>> getMyLeaveRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        PagedResponse<LeaveRequestResponse> response = leaveService.getMyLeaveRequests(principal, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Leave requests retrieved successfully", response));
    }

    @GetMapping("/my-balances")
    public ResponseEntity<ApiResponse<List<LeaveBalanceResponse>>> getMyBalances(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam int year) {
        List<LeaveBalanceResponse> balances = leaveService.getMyBalances(principal, year);
        return ResponseEntity.ok(new ApiResponse<>(true, "Leave balances retrieved successfully", balances));
    }

    @GetMapping("/pending-approvals")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR_MANAGER', 'CEO')")
    public ResponseEntity<ApiResponse<PagedResponse<LeaveRequestResponse>>> getPendingApprovals(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        PagedResponse<LeaveRequestResponse> response = leaveService.getPendingApprovals(principal, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Pending approvals retrieved successfully", response));
    }

    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR_MANAGER', 'CEO')")
    public ResponseEntity<ApiResponse<PagedResponse<LeaveRequestResponse>>> getTeamLeaveRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            Pageable pageable) {
        PagedResponse<LeaveRequestResponse> response = leaveService.getTeamLeaveRequests(principal, pageable);
        return ResponseEntity.ok(new ApiResponse<>(true, "Team leave requests retrieved successfully", response));
    }
}
