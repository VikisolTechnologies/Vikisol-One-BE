package com.vikisol.one.performance.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.performance.dto.*;
import com.vikisol.one.performance.entity.ReviewCycle;
import com.vikisol.one.performance.service.PerformanceService;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;

    @PostMapping("/cycles")
    @PreAuthorize("hasAnyRole('HR_MANAGER','CEO')")
    public ResponseEntity<ApiResponse<?>> createCycle(@Valid @RequestBody ReviewCycleRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Cycle created", performanceService.createCycle(request)));
    }

    @GetMapping("/cycles")
    public ResponseEntity<ApiResponse<?>> getAllCycles(@RequestParam(required = false) String status) {
        if (status != null) {
            return ResponseEntity.ok(new ApiResponse<>(true, "Cycles fetched",
                    performanceService.getCyclesByStatus(ReviewCycle.Status.valueOf(status))));
        }
        return ResponseEntity.ok(new ApiResponse<>(true, "Cycles fetched", performanceService.getAllCycles()));
    }

    @PostMapping("/goals")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO')")
    public ResponseEntity<ApiResponse<?>> createGoal(@Valid @RequestBody GoalRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Goal created", performanceService.createGoal(request)));
    }

    @GetMapping("/goals/my")
    public ResponseEntity<ApiResponse<?>> getMyGoals(@AuthenticationPrincipal UserPrincipal principal,
                                                      @RequestParam UUID cycleId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Goals fetched", performanceService.getMyGoals(principal, cycleId)));
    }

    @GetMapping("/goals/team")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO')")
    public ResponseEntity<ApiResponse<?>> getTeamGoals(@AuthenticationPrincipal UserPrincipal principal,
                                                        @RequestParam UUID cycleId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Goals fetched", performanceService.getTeamGoals(principal, cycleId)));
    }

    @PutMapping("/goals/{id}")
    public ResponseEntity<ApiResponse<?>> updateGoal(@PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Goal updated", performanceService.updateGoal(id, request)));
    }

    @PostMapping("/reviews/self")
    public ResponseEntity<ApiResponse<?>> submitSelfReview(@Valid @RequestBody SelfReviewRequest request,
                                                            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Self review submitted", performanceService.submitSelfReview(request, principal)));
    }

    @PostMapping("/reviews/manager")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO')")
    public ResponseEntity<ApiResponse<?>> submitManagerReview(@Valid @RequestBody ManagerReviewRequest request,
                                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Manager review submitted", performanceService.submitManagerReview(request, principal)));
    }

    @PutMapping("/reviews/{id}/acknowledge")
    public ResponseEntity<ApiResponse<?>> acknowledgeReview(@PathVariable UUID id,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Review acknowledged", performanceService.acknowledgeReview(id, principal)));
    }

    @GetMapping("/reviews/my")
    public ResponseEntity<ApiResponse<?>> getMyReviews(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Reviews fetched", performanceService.getMyReviews(principal)));
    }

    @GetMapping("/reviews/team")
    @PreAuthorize("hasAnyRole('MANAGER','HR_MANAGER','CEO')")
    public ResponseEntity<ApiResponse<?>> getTeamReviews(@AuthenticationPrincipal UserPrincipal principal,
                                                          @RequestParam UUID cycleId) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Reviews fetched", performanceService.getTeamReviews(principal, cycleId)));
    }
}
