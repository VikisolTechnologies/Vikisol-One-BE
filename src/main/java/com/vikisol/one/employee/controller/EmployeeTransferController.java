package com.vikisol.one.employee.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.exception.BadRequestException;
import com.vikisol.one.employee.dto.TransferRequest;
import com.vikisol.one.employee.dto.TransferResponse;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.employee.service.EmployeeTransferService;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Organization transfer history for existing active employees - department/manager/location/cost
// center/business unit changes. Mirrors BackgroundCheckController's self-or-privileged read
// pattern; only CEO/HR_MANAGER/ADMIN may actually initiate a transfer.
@RestController
@RequestMapping("/employees/{employeeId}/transfers")
@RequiredArgsConstructor
public class EmployeeTransferController {

    private final EmployeeTransferService transferService;
    private final EmployeeRepository employeeRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('CEO','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<TransferResponse>> initiateTransfer(
            @PathVariable UUID employeeId, @RequestBody TransferRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID initiatedById = employeeRepository.findByUserId(principal.getId()).map(e -> e.getId()).orElse(null);
        return ResponseEntity.ok(new ApiResponse<>(true, "Transfer recorded",
                transferService.initiateTransfer(employeeId, request, initiatedById)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TransferResponse>>> getHistory(
            @PathVariable UUID employeeId, @AuthenticationPrincipal UserPrincipal principal) {
        boolean privileged = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CEO") || a.getAuthority().equals("ROLE_HR_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
        boolean isSelf = employeeRepository.findById(employeeId)
                .map(e -> e.getUser() != null && e.getUser().getId().equals(principal.getId()))
                .orElse(false);
        if (!privileged && !isSelf) {
            throw new BadRequestException("You do not have permission to view this employee's transfer history");
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Transfer history retrieved", transferService.getHistory(employeeId)));
    }
}
