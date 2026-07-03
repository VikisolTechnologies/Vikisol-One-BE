package com.vikisol.one.employee.dto;

public record OnboardingChecklistRequest(
        Boolean documentsVerified,
        Boolean assetsAssigned,
        Boolean bankDetailsCollected,
        Boolean inductionCompleted
) {
}
