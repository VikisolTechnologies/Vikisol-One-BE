package com.vikisol.one.attendance.controller;

import com.vikisol.one.attendance.dto.*;
import com.vikisol.one.attendance.service.AttendanceService;
import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.leave.dto.LeaveActionRequest;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn(
            @RequestBody CheckInRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AttendanceResponse response = attendanceService.checkIn(principal, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Checked in successfully", response));
    }

    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkOut(
            @RequestBody CheckOutRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        AttendanceResponse response = attendanceService.checkOut(principal, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Checked out successfully", response));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<AttendanceResponse>> getTodayAttendance(
            @AuthenticationPrincipal UserPrincipal principal) {
        AttendanceResponse response = attendanceService.getTodayAttendance(principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Today's attendance retrieved", response));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getMyAttendance(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        List<AttendanceResponse> response = attendanceService.getMyAttendance(principal, start, end);
        return ResponseEntity.ok(new ApiResponse<>(true, "Attendance records retrieved", response));
    }

    @GetMapping("/summary/{employeeId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR_MANAGER', 'CEO', 'ADMIN')")
    public ResponseEntity<ApiResponse<MonthlyAttendanceSummary>> getMonthlyAttendanceSummary(
            @PathVariable UUID employeeId,
            @RequestParam int year,
            @RequestParam int month) {
        MonthlyAttendanceSummary summary = attendanceService.getMonthlyAttendanceSummary(employeeId, year, month);
        return ResponseEntity.ok(new ApiResponse<>(true, "Monthly summary retrieved", summary));
    }

    @PostMapping("/regularization")
    public ResponseEntity<ApiResponse<Void>> requestRegularization(
            @Valid @RequestBody RegularizationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        attendanceService.requestRegularization(request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Regularization request submitted", null));
    }

    @PutMapping("/regularization/{id}/action")
    @PreAuthorize("hasAnyRole('MANAGER', 'HR_MANAGER', 'CEO', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> processRegularization(
            @PathVariable UUID id,
            @Valid @RequestBody LeaveActionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        attendanceService.processRegularization(id, request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Regularization processed", null));
    }
}
