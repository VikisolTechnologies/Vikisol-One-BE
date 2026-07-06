package com.vikisol.one.analytics.controller;

import com.vikisol.one.analytics.dto.ExecutiveAnalyticsResponse;
import com.vikisol.one.analytics.service.ExecutiveAnalyticsService;
import com.vikisol.one.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class ExecutiveAnalyticsController {

    private final ExecutiveAnalyticsService executiveAnalyticsService;

    @GetMapping("/executive")
    @PreAuthorize("hasAnyRole('CEO', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExecutiveAnalyticsResponse>> getExecutiveAnalytics() {
        ExecutiveAnalyticsResponse response = executiveAnalyticsService.getExecutiveAnalytics();
        return ResponseEntity.ok(new ApiResponse<>(true, "Executive analytics retrieved successfully", response));
    }
}
