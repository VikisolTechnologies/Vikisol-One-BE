package com.vikisol.one.report.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.report.dto.AttendanceReportResponse;
import com.vikisol.one.report.dto.DashboardStats;
import com.vikisol.one.report.dto.HeadcountReportResponse;
import com.vikisol.one.report.dto.PayrollReportResponse;
import com.vikisol.one.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStats>> getDashboardStats() {
        DashboardStats stats = reportService.getDashboardStats();
        return ResponseEntity.ok(new ApiResponse<>(true, "Dashboard stats retrieved successfully", stats));
    }

    @GetMapping("/attendance")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'CEO', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AttendanceReportResponse>>> getAttendanceReport(
            @RequestParam int month, @RequestParam int year) {
        List<AttendanceReportResponse> report = reportService.getAttendanceReport(month, year);
        return ResponseEntity.ok(new ApiResponse<>(true, "Attendance report retrieved successfully", report));
    }

    @GetMapping("/attendance/pdf")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'CEO', 'ADMIN')")
    public ResponseEntity<byte[]> downloadAttendanceReportPdf(@RequestParam int month, @RequestParam int year) {
        byte[] pdf = reportService.renderAttendanceReportPdf(month, year);
        String filename = "Attendance_Report_%d_%d.pdf".formatted(month, year);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/payroll")
    @PreAuthorize("hasAnyRole('FINANCE', 'CEO')")
    public ResponseEntity<ApiResponse<PayrollReportResponse>> getPayrollReport(
            @RequestParam int month, @RequestParam int year) {
        PayrollReportResponse report = reportService.getPayrollReport(month, year);
        return ResponseEntity.ok(new ApiResponse<>(true, "Payroll report retrieved successfully", report));
    }

    @GetMapping("/headcount")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'CEO')")
    public ResponseEntity<ApiResponse<HeadcountReportResponse>> getHeadcountReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        HeadcountReportResponse report = reportService.getHeadcountReport(date);
        return ResponseEntity.ok(new ApiResponse<>(true, "Headcount report retrieved successfully", report));
    }
}
