package com.vikisol.one.assessment.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/** Payload posted by Vikisol Arena when a candidate finishes a test. */
public record AssessmentWebhookRequest(
        String arenaSubmissionId,
        @NotBlank String candidateName,
        @NotBlank String candidateEmail,
        String candidatePhone,
        double yearsOfExperience,
        String techStack,
        String resumeUrl,
        @NotBlank String testName,
        LocalDateTime dateTaken,
        double score,
        double maxScore
) {
}
