package com.vikisol.one.assessment.dto;

import com.vikisol.one.assessment.entity.Assessment;

import java.time.LocalDateTime;
import java.util.UUID;

public record AssessmentResponse(
        UUID id,
        UUID candidateId,
        String candidateName,
        String candidateEmail,
        String candidatePhone,
        double yearsOfExperience,
        String techStack,
        String resumeUrl,
        String testName,
        LocalDateTime dateTaken,
        double score,
        double maxScore,
        double percentage,
        Assessment.Status status,
        boolean movedToInterview,
        LocalDateTime createdAt
) {
}
