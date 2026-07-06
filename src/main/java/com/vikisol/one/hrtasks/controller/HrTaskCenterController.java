package com.vikisol.one.hrtasks.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.hrtasks.dto.HrTaskCenterResponse;
import com.vikisol.one.hrtasks.service.HrTaskCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hr-tasks")
@RequiredArgsConstructor
public class HrTaskCenterController {

    private final HrTaskCenterService hrTaskCenterService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CEO', 'HR_MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<HrTaskCenterResponse>> getHrTaskCenter() {
        HrTaskCenterResponse response = hrTaskCenterService.getHrTaskCenter();
        return ResponseEntity.ok(new ApiResponse<>(true, "HR task center retrieved successfully", response));
    }
}
