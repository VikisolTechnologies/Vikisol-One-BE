package com.vikisol.one.employee.dto;

import java.util.UUID;

public record EducationResponse(
        UUID id,
        String degree,
        String university,
        String college,
        Integer yearOfCompletion,
        String gradeOrPercentage,
        String certificateDocumentUrl
) {
}
