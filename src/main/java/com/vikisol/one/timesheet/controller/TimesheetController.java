package com.vikisol.one.timesheet.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.security.service.UserPrincipal;
import com.vikisol.one.timesheet.dto.*;
import com.vikisol.one.timesheet.service.TimesheetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/timesheets")
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetService timesheetService;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody TimesheetEntryRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Entry created", timesheetService.createEntry(request, principal)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> update(@PathVariable UUID id,
                                                  @Valid @RequestBody TimesheetEntryRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Entry updated", timesheetService.updateEntry(id, request, principal)));
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<?>> submit(@Valid @RequestBody TimesheetSubmitRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        timesheetService.submitEntries(request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Entries submitted", null));
    }

    @PutMapping("/{id}/action")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO')")
    public ResponseEntity<ApiResponse<?>> processAction(@PathVariable UUID id, @RequestParam String action,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Action processed", timesheetService.processAction(id, action, principal)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<?>> getMyEntries(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Entries fetched", timesheetService.getMyEntries(principal, start, end)));
    }

    @GetMapping("/team")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO')")
    public ResponseEntity<ApiResponse<?>> getTeamEntries(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Entries fetched", timesheetService.getTeamEntries(principal, start, end)));
    }

    @GetMapping("/project/{projectId}")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<?>> getProjectEntries(@PathVariable UUID projectId,
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Entries fetched", timesheetService.getProjectEntries(projectId, start, end)));
    }
}
