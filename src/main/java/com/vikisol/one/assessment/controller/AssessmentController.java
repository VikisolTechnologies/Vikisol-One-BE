package com.vikisol.one.assessment.controller;

import com.vikisol.one.assessment.dto.AssessmentResponse;
import com.vikisol.one.assessment.dto.AssessmentWebhookRequest;
import com.vikisol.one.assessment.dto.MoveToInterviewRequest;
import com.vikisol.one.assessment.service.AssessmentService;
import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    // Shared secret with Vikisol Arena - this endpoint is permitAll in SecurityConfig since Arena
    // is a separate deployed app with no HRLMS user session, so auth is enforced here instead.
    @Value("${arena.webhook.api-key:}")
    private String webhookApiKey;

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<AssessmentResponse>> ingestResult(
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody AssessmentWebhookRequest request) {
        if (webhookApiKey == null || webhookApiKey.isBlank() || !webhookApiKey.equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiResponse<>(false, "Invalid API key", null));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Assessment result recorded",
                assessmentService.ingestResult(request)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<Page<AssessmentResponse>>> getAssessments(Pageable pageable) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Assessments retrieved",
                assessmentService.getAssessments(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','CEO','ADMIN')")
    public ResponseEntity<ApiResponse<AssessmentResponse>> getAssessment(@PathVariable UUID id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Assessment retrieved",
                assessmentService.getAssessment(id)));
    }

    @PostMapping("/{id}/move-to-interview")
    @PreAuthorize("hasAnyRole('RECRUITER','HR_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<AssessmentResponse>> moveToInterview(
            @PathVariable UUID id, @Valid @RequestBody MoveToInterviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Candidate moved to interview",
                assessmentService.moveToInterview(id, request, principal)));
    }
}
